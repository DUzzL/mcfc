"""Aggregate mcfc diagnostics.tsv files (WORK/diag/<id>.tsv) into results/_unsupported.{md,json}.

Columns of the tsv: kind, id, function, message, command.
Tables: (i) reasons x packs, (ii) failing command word x count x top reason, (iii) macro breakdown by command
word and argument position, (iv) compile failures (COMPILE_ERROR / whole-function fallbacks) by message.
Only individual packs (not combinations) are aggregated.

Usage: python unsupported_report.py [--diag-dir DIR]
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import common as c

csv.field_size_limit(sys.maxsize)
MACRO_MSG = "Macros are not supported in this location"
EXEC_KEYWORDS = {"if", "unless", "as", "at", "positioned", "rotated", "facing", "align", "anchored", "in", "on",
                 "summon", "store", "run"}
EXEC_TWO = {"if", "unless", "store"}  # followed by a type word (block/entity/score/...; result/success)


def normalise(msg: str) -> str:
    """Strip line-specific parts (numbers, quoted values, ids in backticks) from a diagnostic message."""
    m = msg.strip()
    m = re.sub(r"\"[^\"]*\"", "<str>", m)
    m = re.sub(r"'[^']*'", "<str>", m)
    m = re.sub(r"(?<![\w<:])-?\d+(\.\d+)?[a-zA-Z]?\b", "N", m)
    # replace resource ids outside grammar placeholders such as <predicate:resource_location>
    parts = re.split(r"(<[^>]*>)", m)
    m = "".join(x if x.startswith("<") else re.sub(r"\b[a-z0-9_.-]+:[a-z0-9_./-]+\b", "<id>", x) for x in parts)
    m = re.sub(r"\s+", " ", m)
    return m[:200]


def tokens_of(command: str) -> list[str]:
    cmd = command.strip()
    if cmd.startswith("$"):
        cmd = cmd[1:]
    return cmd.split()


def exec_clause_at(toks: list[str], idx: int) -> tuple[str, int]:
    """Clause label and argument position of token idx inside an execute chain."""
    clause, start = "execute", 0
    i = 1
    while i <= idx and i < len(toks):
        t = toks[i]
        if t in EXEC_KEYWORDS and (i == 1 or toks[i - 1] not in ("positioned", "rotated", "facing") or t == "run"):
            if t in EXEC_TWO and i + 1 < len(toks):
                clause, start = f"execute {t} {toks[i + 1]}", i + 1
                i += 2
                continue
            if t == "run":
                inner, pos = label_for(toks[i + 1:], idx - (i + 1))
                return f"execute run {inner}", pos
            clause, start = f"execute {t}", i
        i += 1
    return clause, max(idx - start, 0)


def label_for(toks: list[str], idx: int | None = None) -> tuple[str, int]:
    """Command label (word plus subcommand for execute/data/scoreboard) and arg position of token idx."""
    if not toks:
        return "?", 0
    w = toks[0]
    pos = idx if idx is not None else 0
    if w == "execute":
        if idx is not None:
            return exec_clause_at(toks, idx)
        return (f"execute {toks[1]}" + (f" {toks[2]}" if toks[1] in EXEC_TWO and len(toks) > 2 else "")
                if len(toks) > 1 else "execute"), 0
    if w == "data" and len(toks) > 2:
        return " ".join(toks[:3] if toks[1] == "modify" else toks[:3]), pos
    if w == "scoreboard" and len(toks) > 2:
        return " ".join(toks[:3]), pos
    return w, pos


def command_label(command: str, message: str) -> str:
    toks = tokens_of(command)
    # grammar path inside the message: "... @ execute if predicate <predicate:resource_location>"
    if message.startswith(MACRO_MSG) and toks:
        return macro_position(command)[0]
    um = re.match(r"Unknown command: (\S+)", message)
    if um:  # the failing command is the unknown word itself, also behind `execute ... run`
        return um.group(1)
    m = re.search(r"@ ((?:[a-z_]+ )+)<", message)
    if m and toks:
        path = m.group(1).strip()
        if toks[0] == path.split()[0]:
            return path
        if toks[0] == "execute":  # the grammar path describes the command after `run`
            return "execute run " + path
    return label_for(toks)[0]


def macro_position(command: str) -> tuple[str, str]:
    toks = tokens_of(command)
    for i, t in enumerate(toks):
        if "$(" in t:
            label, pos = label_for(toks, i)
            where = "command word" if i == 0 else f"arg {pos}"
            text = " ".join(toks)
            prefix = text[: text.find("$(")]
            if prefix.count("{") > prefix.count("}"):
                where += " (inside {...})"
            return label, where
    return label_for(toks)[0], "?"


def load(diag_dir: Path, ids: list[str]) -> list[dict]:
    rows = []
    for pid in ids:
        f = diag_dir / f"{pid}.tsv"
        if not f.exists():
            continue
        with open(f, encoding="utf-8", newline="") as fh:
            rd = csv.reader(fh, delimiter="\t", quoting=csv.QUOTE_NONE)
            header = next(rd, None)
            for r in rd:
                if len(r) < 5:
                    r = r + [""] * (5 - len(r))
                rows.append({"pack": pid, "kind": r[0], "id": r[1], "function": r[2], "message": r[3],
                             "command": "\t".join(r[4:])})
    return rows


def aggregate(rows: list[dict]) -> dict:
    reasons: dict[str, dict] = {}
    words: dict[str, dict] = {}
    macros: dict[tuple[str, str], int] = Counter()
    macro_by_word: Counter = Counter()
    compile_fail: dict[tuple[str, str], dict] = {}
    for r in rows:
        key = f"{r['kind']}: {normalise(r['message'])}"
        e = reasons.setdefault(key, {"commands": 0, "packs": Counter(), "ids": Counter()})
        e["commands"] += 1
        e["packs"][r["pack"]] += 1
        e["ids"][r["id"]] += 1
        label = command_label(r["command"], r["message"]) if r["command"] else "(whole function)"
        w = words.setdefault(label, {"count": 0, "reasons": Counter(), "packs": Counter()})
        w["count"] += 1
        w["reasons"][key] += 1
        w["packs"][r["pack"]] += 1
        if r["message"].startswith(MACRO_MSG):
            lab, where = macro_position(r["command"])
            macros[(lab, where)] += 1
            macro_by_word[lab] += 1
        if r["kind"] == "COMPILE_ERROR" or "whole function" in r["message"]:
            cf = compile_fail.setdefault((r["kind"], normalise(r["message"])),
                                         {"count": 0, "functions": Counter(), "packs": Counter()})
            cf["count"] += 1
            cf["functions"][r["function"]] += 1
            cf["packs"][r["pack"]] += 1
    return {"reasons": reasons, "words": words, "macros": macros, "macroByWord": macro_by_word,
            "compileFail": compile_fail, "total": len(rows)}


def top(counter: Counter, n: int) -> str:
    return ", ".join(f"{k} ({v})" if not isinstance(v, str) else k for k, v in counter.most_common(n))


def write_reports(agg: dict, packs_seen: list[str], dest_md: Path, dest_json: Path) -> None:
    reasons = sorted(agg["reasons"].items(), key=lambda kv: -kv[1]["commands"])
    words = sorted(agg["words"].items(), key=lambda kv: -kv[1]["count"])
    js = {
        "packs": packs_seen, "totalDiagnostics": agg["total"],
        "reasons": [{"reason": k, "commands": v["commands"], "packs": dict(v["packs"]), "ids": dict(v["ids"])}
                    for k, v in reasons],
        "commandWords": [{"command": k, "count": v["count"], "reasons": dict(v["reasons"].most_common(10)),
                          "packs": dict(v["packs"])} for k, v in words],
        "macros": [{"command": k[0], "position": k[1], "count": n} for k, n in agg["macros"].most_common()],
        "compileFailures": [{"kind": k[0], "message": k[1], "count": v["count"], "functions": dict(v["functions"]),
                             "packs": dict(v["packs"])} for k, v in sorted(agg["compileFail"].items(),
                                                                          key=lambda kv: -kv[1]["count"])],
    }
    dest_json.write_text(json.dumps(js, indent=1), encoding="utf-8")

    L = ["# unsupported commands (mcfc diagnostics)", "",
         f"{agg['total']} diagnostics from {len(packs_seen)} individual packs (combinations excluded). "
         "Full aggregation: `_unsupported.json`.", "",
         "## (i) reasons", "", "| reason | commands | packs |", "|---|---|---|"]
    for k, v in reasons[:25]:
        L.append(f"| {k[:110]} | {v['commands']} | {len(v['packs'])}: {top(v['packs'], 4)} |")
    if len(reasons) > 25:
        L.append(f"| ... {len(reasons) - 25} more reasons in the json | | |")
    L += ["", "## (ii) failing command word", "", "| command | count | top reason |", "|---|---|---|"]
    for k, v in words[:30]:
        reason, n = v["reasons"].most_common(1)[0]
        L.append(f"| `{k}` | {v['count']} | {reason[:90]} ({n}) |")
    L += ["", f"## (iii) \"{MACRO_MSG}\"", "",
          f"{sum(agg['macros'].values())} commands. By command word:",
          "", ", ".join(f"`{w}` {n}" for w, n in agg["macroByWord"].most_common(15)), "",
          "| command | argument position | count |", "|---|---|---|"]
    for (w, where), n in agg["macros"].most_common(25):
        L.append(f"| `{w}` | {where} | {n} |")
    L += ["", "## (iv) compile failures (COMPILE_ERROR / whole-function fallback)", ""]
    if not agg["compileFail"]:
        L.append("none")
    else:
        L += ["| kind | message | count | packs | function ids (first 4) |", "|---|---|---|---|---|"]
        for (kind, msg), v in sorted(agg["compileFail"].items(), key=lambda kv: -kv[1]["count"])[:25]:
            fns = ", ".join(f"`{f}`" for f, _ in v["functions"].most_common(4))
            L.append(f"| {kind} | {msg[:90]} | {v['count']} | {top(v['packs'], 3)} | {fns} |")
    dest_md.write_text("\n".join(L) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--diag-dir", default=str(c.WORK / "diag"))
    args = ap.parse_args()
    m = c.load_manifest() or {}
    ids = [p["id"] for p in m.get("packs", []) if not p.get("skip")]
    diag = Path(args.diag_dir)
    rows = load(diag, ids)
    seen = sorted({r["pack"] for r in rows} | {i for i in ids if (diag / f"{i}.tsv").exists()})
    agg = aggregate(rows)
    write_reports(agg, seen, c.RESULTS_DIR / "_unsupported.md", c.RESULTS_DIR / "_unsupported.json")
    c.log(f"{agg['total']} diagnostics, {len(agg['reasons'])} reasons, {len(agg['words'])} command words")
    return 0


if __name__ == "__main__":
    c.main_guard(main)
