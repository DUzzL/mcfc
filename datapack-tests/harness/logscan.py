"""Scan the logs of one run and aggregate mcfc / vanilla diagnostics.

Usage: python logscan.py <run_dir>   (writes <run_dir>/logscan.json)
"""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import common as c

SUMMARY_RX = re.compile(r"\[Summary\] (\d+) function\(s\) found, (\d+) parsed, (\d+) compiled, (\d+) registered")
PARTIAL_RX = re.compile(r"Partially compiled (\d+) command\(s\) with vanilla fallback\. Reasons:")
REASON_RX = re.compile(r"^ {2}(\S.*): (\d+) command\(s\)(?: \((.*)\))?\s*$")
WHOLE_RX = re.compile(r"Function (\S+) falls back to vanilla: (.*)")
FAILED_COMPILE_RX = re.compile(r"Failed to compile function (\S+)")
BATCH_RX = re.compile(r"Batch (\S+) fails but none of its functions fails alone")
THREW_RX = re.compile(r"Compiled function (\S+) threw; running it through vanilla from now on")
SOF_RX = re.compile(r"Stack overflow in compiled function (\S+)")
HANDLE_RX = re.compile(r"Failed to create function handle")
UNKNOWN_CMD_RX = re.compile(r"Unknown command: (\S+)")
EXC_RX = re.compile(r"^(?:Caused by: )?((?:[A-Za-z_][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error|Throwable))(?::\s*(.*))?$")
LOAD_FAIL_RX = re.compile(r"(Failed to load function (\S+)|Couldn't load tag .*|Failed to load \w+ .*)")
DEBUG_ROW_RX = re.compile(r"^\[(\w+)\s*\]\s+(\S+)\s+(\d+)\s+(\d+)")
DEBUG_SUM_RX = re.compile(r"^Summary:\s*(.*)$")
IGNORABLE = ("Win32Exception", "Perflib")


def read_lines(p: Path):
    if not p.exists():
        return []
    with open(p, "rb") as f:
        return [ln.decode("utf-8", errors="replace").rstrip("\r\n") for ln in f]


def scan_run(run_dir: Path) -> dict:
    run_dir = Path(run_dir)
    latest = read_lines(run_dir / "logs" / "latest.log")
    console = read_lines(run_dir / "console.log")
    out: dict = {"files": {"latest.log": len(latest), "console.log": len(console)}}

    # --- mcfc lines (latest.log) -----------------------------------------
    summaries = []
    partial_total = 0
    reasons: dict[str, dict] = {}
    unsupported: Counter = Counter()
    whole: dict[str, str] = {}
    whole_reasons: Counter = Counter()
    failed_compile: list[str] = []
    batch_fail: list[str] = []
    threw: list[str] = []
    sof: list[str] = []
    handle_fail = 0
    compiler_errors: list[str] = []
    i = 0
    while i < len(latest):
        ln = latest[i]
        m = SUMMARY_RX.search(ln)
        if m:
            summaries.append(dict(zip(("found", "parsed", "compiled", "registered"), map(int, m.groups()))))
        m = PARTIAL_RX.search(ln)
        if m:
            partial_total += int(m.group(1))
            j = i + 1
            while j < len(latest):
                rm = REASON_RX.match(latest[j])
                if not rm:
                    break
                msg, n, nss = rm.group(1), int(rm.group(2)), rm.group(3)
                r = reasons.setdefault(msg, {"commands": 0, "namespaces": {}})
                r["commands"] += n
                for part in (nss or "").split(","):
                    if ":" in part:
                        ns, cnt = part.rsplit(":", 1)
                        try:
                            r["namespaces"][ns.strip()] = r["namespaces"].get(ns.strip(), 0) + int(cnt)
                        except ValueError:
                            pass
                um = UNKNOWN_CMD_RX.search(msg)
                if um:
                    unsupported[um.group(1)] += n
                j += 1
            i = j
            continue
        m = WHOLE_RX.search(ln)
        if m:
            whole[m.group(1)] = m.group(2).strip()[:300]
            whole_reasons[m.group(2).strip()[:120]] += 1
        for rx, dest in ((FAILED_COMPILE_RX, failed_compile), (BATCH_RX, batch_fail), (THREW_RX, threw), (SOF_RX, sof)):
            m = rx.search(ln)
            if m:
                dest.append(m.group(1))
        if HANDLE_RX.search(ln):
            handle_fail += 1
        if "[Function Compiler/ERROR]" in ln or re.search(r"\[mcfc[^\]]*/ERROR\]", ln):
            compiler_errors.append(ln.split("]: ", 1)[-1][:300])
        i += 1
    # first decisive compiler error: the ERROR line plus the exception line that follows it
    decisive = None
    for idx, ln in enumerate(latest):
        if "/ERROR]" in ln and ("Function Compiler" in ln or "mcfc" in ln.lower()):
            msg = ln.split("]: ", 1)[-1][:200]
            exc = None
            for nxt in latest[idx + 1: idx + 4]:
                m = EXC_RX.match(nxt)
                if m:
                    exc = f"{m.group(1)}: {(m.group(2) or '')[:200]}"
                    break
            decisive = f"{msg} -> {exc}" if exc else msg
            break
    out["mcfc"] = {
        "decisiveError": decisive,
        "summaries": summaries,
        "lastSummary": summaries[-1] if summaries else None,
        "partialFallbackCommands": partial_total,
        "partialReasons": dict(sorted(reasons.items(), key=lambda kv: -kv[1]["commands"])),
        "unsupportedCommands": dict(unsupported.most_common()),
        "wholeFallbacks": whole,
        "wholeFallbackReasons": dict(whole_reasons.most_common()),
        "compileFailures": failed_compile,
        "batchFailures": batch_fail,
        "runtimeDisables": threw,
        "stackOverflows": sof,
        "handleFailures": handle_fail,
        "compilerErrors": compiler_errors[:30],
        "compilerErrorCount": len(compiler_errors),
    }

    # --- exceptions and vanilla load errors ------------------------------
    exc_src = console if console else latest
    exc_count: Counter = Counter()
    exc_first: dict[str, str] = {}
    ignored = 0
    for ln in exc_src:
        m = EXC_RX.match(ln)
        if m:
            cls, msg = m.group(1), (m.group(2) or "")
            if any(t in cls or t in msg for t in IGNORABLE):
                ignored += 1
                continue
            exc_count[cls] += 1
            exc_first.setdefault(cls, msg[:200])
    out["exceptions"] = {k: {"count": n, "first": exc_first[k]} for k, n in exc_count.most_common()}
    out["exceptionsIgnored"] = ignored
    level = Counter()
    for ln in latest:
        m = re.match(r"^\[[\d:]+\] \[[^/\]]+/(\w+)\]", ln)
        if m:
            level[m.group(1)] += 1
    out["logLevels"] = dict(level)
    load_errors = []
    for ln in latest:
        m = LOAD_FAIL_RX.search(ln)
        if m and "ERROR" in ln[:60]:
            load_errors.append(ln[:300])
    out["vanillaLoadErrors"] = {"count": len(load_errors), "sample": load_errors[:20]}

    # --- crash reports -----------------------------------------------------
    cr = run_dir / "crash-reports"
    out["crashReports"] = sorted(p.name for p in cr.iterdir()) if cr.exists() else []
    if out["crashReports"]:
        first = read_lines(cr / out["crashReports"][0])[:12]
        out["crashHead"] = [x[:200] for x in first]

    # --- mcfc-debug.log ----------------------------------------------------
    dbg = read_lines(run_dir / "logs" / "mcfc-debug.log")
    if dbg:
        by_status: Counter = Counter()
        total = vanilla = 0
        others = []
        header = ""
        for ln in dbg:
            m = DEBUG_SUM_RX.match(ln)
            if m and not header:
                header = m.group(1)
            m = DEBUG_ROW_RX.match(ln)
            if not m:
                continue
            status, fid, tot, van = m.group(1), m.group(2), int(m.group(3)), int(m.group(4))
            by_status[status] += 1
            total += tot
            vanilla += van
            if status != "compiled":
                others.append({"id": fid, "status": status, "calls": tot, "vanillaCalls": van})
        others.sort(key=lambda x: -x["calls"])
        out["mcfcDebug"] = {
            "header": header, "functionsByStatus": dict(by_status),
            "totalCalls": total, "vanillaCalls": vanilla, "compiledCalls": total - vanilla,
            "compiledCallShare": round((total - vanilla) / total, 4) if total else None,
            "nonCompiled": others[:200], "nonCompiledCount": len(others),
        }
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run_dir")
    args = ap.parse_args()
    res = scan_run(Path(args.run_dir))
    (Path(args.run_dir) / "logscan.json").write_text(json.dumps(res, indent=1), encoding="utf-8")
    print(json.dumps({k: res[k] for k in ("mcfc",) if k in res}, indent=1)[:2000])
    return 0


if __name__ == "__main__":
    c.main_guard(main)
