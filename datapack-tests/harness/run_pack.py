"""Run one pack (or combination) through base1, base2 and mcfc, compare the dumps and scan the logs.

Order (never more than 2 servers, perf only on a quiet machine):
  1. base1 + base2 concurrently (no perf)
  2. mcfc solo (with perf unless --no-perf)
  3. perfbase solo: a vanilla run that does the same steps and then the perf measurement
  4. on findings and --confirm (default): a second attempt of 1+2 under WORK\\runs\\<id>\\attempt2;
     only findings present in both attempts are "confirmed", the rest is "flaky".

Usage: python run_pack.py <manifest pack/combination id> [--no-perf] [--no-confirm] [--ticks N] [--reps N]
       python run_pack.py <label> --zip path/to/pack.zip [--kind function|worldgen|mixed]
Raw data: WORK\\runs\\<id>\\{base1,base2,mcfc,perfbase}; report: datapack-tests\\results\\<id>.{md,json}
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import common as c
import packs as pk
from compare import compare_runs, write_md
from logscan import scan_run
from run_variant import free_port


# ---------------------------------------------------------------------------
# running
# ---------------------------------------------------------------------------

def launch(args, spec, label: str, root: Path, port: int, rport: int, perf: bool) -> subprocess.Popen:
    cmd = [sys.executable, str(c.HARNESS_DIR / "run_variant.py"), args.id, "--label", label,
           "--run-dir", str(root / label), "--port", str(port), "--rcon-port", str(rport),
           "--ticks", str(args.ticks), "--reps", str(args.reps), "--seed", str(args.seed),
           "--kind", spec["kind"], "--cmd-timeout", str(args.cmd_timeout),
           "--exercise-budget", str(args.exercise_budget)]
    for z in args.zip or []:
        cmd += ["--zip", z]
    if perf:
        cmd.append("--perf")
    if args.max_functions:
        cmd += ["--max-functions", str(args.max_functions)]
    if args.no_exercise:
        cmd.append("--no-exercise")
    root.mkdir(parents=True, exist_ok=True)
    out = open(root / f"{label}.stdout.log", "w", encoding="utf-8")
    return subprocess.Popen(cmd, stdout=out, stderr=subprocess.STDOUT, cwd=c.HARNESS_DIR)


def run_group(args, spec, root: Path, labels: list[str], perf: bool, durations: dict, tag: str) -> None:
    t0 = time.time()
    procs = {}
    port = 25600
    for lbl in labels:
        port = free_port(port)
        rport = free_port(port + 1)
        procs[lbl] = launch(args, spec, lbl, root, port, rport, perf)
        port = max(port, rport) + 1
        time.sleep(0.5)
    for lbl, pr in procs.items():
        pr.wait()
        durations[f"{tag}{lbl}"] = round(time.time() - t0, 1)
        c.log(f"{tag}{lbl} finished rc={pr.returncode} after {durations[f'{tag}{lbl}']}s")


def run_attempt(args, spec, root: Path, perf: bool, durations: dict, tag: str) -> None:
    root.mkdir(parents=True, exist_ok=True)
    run_group(args, spec, root, ["base1", "base2"], False, durations, tag)
    run_group(args, spec, root, ["mcfc"], perf, durations, tag)
    if perf:
        run_group(args, spec, root, ["perfbase"], True, durations, tag)


# ---------------------------------------------------------------------------
# analysis
# ---------------------------------------------------------------------------

def load_run(root: Path, label: str) -> dict:
    p = root / label / "run.json"
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    return {"error": "run.json missing", "crash": True}


def analyze(spec, root: Path, extras: list[Path] | None = None) -> dict:
    """Compare base1/base2/mcfc of one attempt; extras are further vanilla runs used for the noise estimate."""
    extras = [d for d in (extras or []) if (d / "dumps" / "final.json").exists()]
    labels = ["base1", "base2", "mcfc"] + (["perfbase"] if (root / "perfbase").exists() else [])
    runs = {k: load_run(root, k) for k in labels}
    scans = {}
    for k in labels:
        if (root / k).exists():
            scans[k] = scan_run(root / k)
            (root / k / "logscan.json").write_text(json.dumps(scans[k], indent=1), encoding="utf-8")
    worldgen = spec["kind"] != "function"
    final = compare_runs(root, "final", lowconf=worldgen, extra_baselines=extras)
    (root / "compare-final.json").write_text(json.dumps(final, indent=1), encoding="utf-8")
    write_md(final, root / "compare-final.md")
    gen = None
    if worldgen:
        gen = compare_runs(root, "gen", gen_only=True, extra_baselines=extras)
        (root / "compare-gen.json").write_text(json.dumps(gen, indent=1), encoding="utf-8")
        write_md(gen, root / "compare-gen.md")
    return {"runs": runs, "scans": scans, "final": final, "gen": gen}


def high_paths(an: dict) -> set[tuple[str, str]]:
    out = {("final", p) for p in an["final"].get("findingPaths", [])}
    if an["gen"]:
        out |= {("gen", p) for p in an["gen"].get("findingPaths", [])}
    return out


def finding_records(an: dict) -> dict[tuple[str, str], dict]:
    out = {}
    for f in an["final"].get("findings", []):
        out[("final", f["path"])] = f
    if an["gen"]:
        for f in an["gen"].get("findings", []):
            out[("gen", f["path"])] = f
    return out


def attempt_failed(an: dict) -> bool:
    if an["final"]["status"] == "INCOMPLETE":
        return True
    return any(an["runs"][k].get("crash") or an["runs"][k].get("error") for k in ("base1", "base2", "mcfc"))


def perf_summary(run: dict) -> dict | None:
    p = run.get("perf")
    if not p:
        return None
    return {"median": p["medianMspt"], "min": p.get("minMspt"), "max": p.get("maxMspt"), "lines": p["lines"]}


def build_report(args, spec, a1: dict, a2: dict | None, durations: dict) -> dict:
    runs, scans = a1["runs"], a1["scans"]
    mc_scan = scans["mcfc"]["mcfc"]
    last = mc_scan["lastSummary"]
    found = last["found"] if last else None
    compiled = last["compiled"] if last else None
    mcrun = runs["mcfc"]
    if found is None:
        m = re.search(r"Compiled Function Count:\s*(\d+)\s*Total Function Count:\s*(\d+)",
                      mcrun.get("mcfcStateFinal") or mcrun.get("mcfcState") or "")
        if m:
            compiled, found = int(m.group(1)), int(m.group(2))
    state_load = mcrun.get("mcfcStateName")
    state_final = mcrun.get("mcfcStateFinalName") or state_load
    decisive = None
    if state_final != "READY_ON":
        decisive = mc_scan.get("decisiveError")
        if not decisive:
            exc = scans["mcfc"]["exceptions"]
            if exc:
                cls, x = next(iter(exc.items()))
                decisive = f"{cls}: {x['first']}"

    notes: list[str] = []
    for lbl in ("mcfc", "base1", "base2"):
        for n in runs[lbl].get("notes", []):
            if not n.startswith("forceload") and n not in notes:
                notes.append(n)
    problems = []
    for lbl in ("base1", "base2", "mcfc"):
        if runs[lbl].get("crash") or runs[lbl].get("error"):
            problems.append(f"{lbl} run failed: {runs[lbl].get('error', 'crash')}")

    # exerciser coverage must match between variants, otherwise diffs may be artifacts
    ex = {k: runs[k].get("exercise") for k in ("base1", "base2", "mcfc")}
    ran = {k: (v or {}).get("ran") for k, v in ex.items()}
    if len(set(ran.values())) > 1:
        notes.append(f"exerciser coverage differs between variants (ran: {ran}); diffs may be artifacts")
    slow = {}
    for k, v in ex.items():
        for s_ in (v or {}).get("slow", []):
            slow.setdefault(s_["id"], []).append(k)
    budget = [k for k, v in ex.items() if (v or {}).get("budgetExceeded")]
    if budget:
        notes.append(f"exerciser time budget exceeded in: {', '.join(budget)}")

    # confirmation
    p1 = high_paths(a1)
    recs = finding_records(a1)
    confirm_info = {"enabled": args.confirm, "ran": False}
    if a2 is not None:
        confirm_info["ran"] = True
        if attempt_failed(a2):
            confirm_info["note"] = "second attempt failed; findings left unconfirmed"
            confirmed, flaky = p1, set()
        else:
            p2 = high_paths(a2)
            confirmed, flaky = p1 & p2, p1 - p2
            confirm_info["onlyInSecondAttempt"] = len(p2 - p1)
    else:
        confirmed, flaky = p1, set()
    conf_list = [recs[k] | {"dump": k[0]} for k in sorted(confirmed) if k in recs]
    flaky_list = [recs[k] | {"dump": k[0]} for k in sorted(flaky) if k in recs]

    fallback_issue = bool(mc_scan["compilerErrorCount"] or (state_final and state_final != "READY_ON")
                          or mc_scan["compileFailures"] or mc_scan["wholeFallbacks"] or mc_scan["runtimeDisables"]
                          or mc_scan["batchFailures"] or mc_scan["stackOverflows"] or mc_scan["handleFailures"]
                          or (last and found != compiled))
    if mcrun.get("crash") or mcrun.get("error"):
        status = "CRASH"
    elif any(runs[b].get("crash") or runs[b].get("error") for b in ("base1", "base2")) or a1["final"]["status"] == "INCOMPLETE":
        status = "INCOMPLETE"
    elif confirmed:
        status = "DIFF"
    elif fallback_issue:
        status = "FALLBACK-ISSUE"
    else:
        status = "OK"

    pf_m, pf_b = perf_summary(runs["mcfc"]), perf_summary(runs.get("perfbase", {}))
    exc_tot = {k: sum(v["count"] for v in scans[k]["exceptions"].values()) for k in scans}
    gen = a1["gen"]
    return {
        "id": args.id, "status": status, "kind": spec["kind"], "packs": [p["id"] for p in spec["packs"]],
        "problems": problems, "notes": notes,
        "mcfcState": {"afterLoad": state_load, "final": state_final, "decisiveError": decisive,
                      "text": mcrun.get("mcfcStateFinal") or mcrun.get("mcfcState")},
        "compile": {"found": found, "compiled": compiled,
                    "rate": round(compiled / found, 4) if found else None,
                    "partialFallbackCommands": mc_scan["partialFallbackCommands"],
                    "wholeFallbacks": mc_scan["wholeFallbacks"],
                    "compileFailures": mc_scan["compileFailures"],
                    "compilerErrors": mc_scan["compilerErrors"],
                    "runtimeDisables": mc_scan["runtimeDisables"],
                    "topPartialReasons": {k: v["commands"] for k, v in list(mc_scan["partialReasons"].items())[:15]},
                    "unsupportedCommands": mc_scan["unsupportedCommands"]},
        "debug": scans["mcfc"].get("mcfcDebug"),
        "compare": {
            "confirmedFindings": len(conf_list), "flakyFindings": len(flaky_list),
            "lowConfidence": a1["final"].get("lowConfidenceTotal", 0),
            "noise": a1["final"]["noiseTotal"],
            "genFindings": gen["findingsTotal"] if gen else None,
            "genNoise": gen["noiseTotal"] if gen else None,
            "confirmation": confirm_info,
            "sections": a1["final"]["sections"],
            "confirmed": conf_list[:200], "flaky": flaky_list[:100],
            "lowConfidenceSample": a1["final"].get("lowConfidence", [])[:40],
        },
        "exceptions": {k: scans[k]["exceptions"] for k in scans},
        "exceptionTotals": exc_tot,
        "exercise": ex,
        "slowFunctions": slow,
        "perf": {"mcfc": pf_m, "vanilla": pf_b,
                 "note": "solo runs, warmup sprint 200 then sprint 1200 x reps"} if pf_m or pf_b else None,
        "durationsSeconds": durations,
        "gamerules": {k: v["name"] for k, v in runs["base1"].get("gamerules", {}).items()},
        "ticks": args.ticks, "seed": args.seed,
    }


def fmt_perf(p: dict | None) -> str:
    if not p:
        return "n/a"
    return f"median {p['median']} ms (min {p['min']}, max {p['max']})"


def write_md_report(rep: dict, dest: Path) -> None:
    cp, cm, st = rep["compile"], rep["compare"], rep["mcfcState"]
    L = [f"# {rep['id']}: {rep['status']}", "",
         f"- kind: {rep['kind']}, packs: {', '.join(rep['packs'])}",
         f"- mcfc final state: {st['final']} (after load: {st['afterLoad']})",
         f"- compile: {cp['compiled']}/{cp['found']} functions compiled"
         + (f" ({cp['rate'] * 100:.1f}%)" if cp["rate"] is not None else "")
         + f", partial fallback commands: {cp['partialFallbackCommands']}",
         f"- whole-function fallbacks: {len(cp['wholeFallbacks'])}, compile failures: {len(cp['compileFailures'])}, "
         f"runtime disables: {len(cp['runtimeDisables'])}"]
    if st["final"] != "READY_ON":
        L.append(f"- decisive mcfc error: {st['decisiveError'] or 'none found in log'}")
    conf = cm["confirmation"]
    L.append(f"- findings: confirmed {cm['confirmedFindings']}, flaky {cm['flakyFindings']}, "
             f"low-confidence {cm['lowConfidence']}, noise paths {cm['noise']}"
             + (f"; worldgen (gen dump) findings {cm['genFindings']}" if cm["genFindings"] is not None else "")
             + ("" if conf["ran"] else ("; not re-run (no findings)" if conf["enabled"] else "; confirmation disabled"))
             + (f"; {conf['note']}" if conf.get("note") else ""))
    L.append(f"- exceptions (non-ignorable lines): base1={rep['exceptionTotals']['base1']}, "
             f"base2={rep['exceptionTotals']['base2']}, mcfc={rep['exceptionTotals']['mcfc']}")
    if rep["perf"]:
        pf = rep["perf"]
        L.append(f"- MSPT (solo, sprint 1200 x3): vanilla {fmt_perf(pf['vanilla'])}; mcfc {fmt_perf(pf['mcfc'])}")
    d = rep.get("debug")
    if d:
        L.append(f"- mcfc debug: {d['header']}; compiled-call share {d['compiledCallShare']}")
    ex = rep["exercise"].get("mcfc")
    if ex:
        L.append(f"- exerciser (mcfc): ran {ex['ran']} of {ex['candidates']} candidate functions (macro skipped "
                 f"{ex['skippedMacro']}, name skipped {ex['skippedName']}, scenario skipped {ex.get('skippedScenario', 0)}), "
                 f"errors {ex['replyError']}, slow {len(ex.get('slow', []))}")
    if rep["slowFunctions"]:
        L.append("- slow functions (> timeout): " + ", ".join(f"{k} ({'/'.join(v)})" for k, v in list(rep["slowFunctions"].items())[:8]))
    for n in rep["notes"] + rep["problems"]:
        L.append(f"- note: {n}")
    if cp["topPartialReasons"]:
        L += ["", "## top partial-fallback reasons"] + [f"- {k}: {v}" for k, v in cp["topPartialReasons"].items()]
    if cp["wholeFallbacks"]:
        L += ["", "## whole-function fallbacks (first 15)"] + [f"- {k}: {v}" for k, v in list(cp["wholeFallbacks"].items())[:15]]
    if cp["compilerErrors"]:
        L += ["", "## compiler errors (first 5)"] + [f"- {e}" for e in cp["compilerErrors"][:5]]
    if cp["runtimeDisables"]:
        L += ["", "## runtime disables"] + [f"- {k}" for k in cp["runtimeDisables"][:30]]
    if cm["confirmed"]:
        L += ["", "## confirmed findings (first 20)"]
        for f in cm["confirmed"][:20]:
            L.append(f"- [{f['dump']}] `{f['path']}`: base={f['base'][:150]} | mcfc={f['mcfc'][:150]}")
    if cm["flaky"]:
        L += ["", "## flaky findings (first 10, not reproduced in the second attempt)"]
        for f in cm["flaky"][:10]:
            L.append(f"- [{f['dump']}] `{f['path']}`")
    if cm["lowConfidenceSample"]:
        L += ["", f"## low-confidence findings ({cm['lowConfidence']}, first 10; not counted)"]
        for f in cm["lowConfidenceSample"][:10]:
            L.append(f"- `{f['path']}`")
    for k, v in rep["exceptions"].items():
        if v:
            L += ["", f"## exceptions {k}"] + [f"- {cls} x{x['count']}: {x['first'][:120]}" for cls, x in list(v.items())[:8]]
    dest.write_text("\n".join(L) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("id")
    ap.add_argument("--zip", action="append")
    ap.add_argument("--kind", choices=["function", "worldgen", "mixed"])
    ap.add_argument("--no-perf", action="store_true", help="skip the solo mcfc/vanilla perf measurement")
    ap.add_argument("--confirm", action=argparse.BooleanOptionalAction, default=True,
                    help="re-run once when there are findings (default on; --no-confirm to disable)")
    ap.add_argument("--ticks", type=int, default=200)
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--seed", default="20261026")
    ap.add_argument("--max-functions", type=int, default=0)
    ap.add_argument("--no-exercise", action="store_true")
    ap.add_argument("--cmd-timeout", type=float, default=60.0)
    ap.add_argument("--exercise-budget", type=float, default=300.0)
    args = ap.parse_args()
    c.ensure_dirs()
    spec = pk.resolve_spec(args.id, args.zip, args.kind)
    root = c.RUNS / args.id
    if root.exists():
        shutil.rmtree(root)
    root.mkdir(parents=True)
    c.RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    durations: dict[str, float] = {}

    run_attempt(args, spec, root, not args.no_perf, durations, "")
    a1 = analyze(spec, root, [root / "perfbase"])
    a2 = None
    if args.confirm and high_paths(a1) and not attempt_failed(a1):
        c.log(f"{len(high_paths(a1))} finding(s); running a second attempt to confirm")
        root2 = root / "attempt2"
        run_attempt(args, spec, root2, False, durations, "attempt2-")
        # every vanilla run of both attempts feeds the noise estimate of each attempt
        a1 = analyze(spec, root, [root / "perfbase", root2 / "base1", root2 / "base2"])
        a2 = analyze(spec, root2, [root / "base1", root / "base2", root / "perfbase"])
    rep = build_report(args, spec, a1, a2, durations)
    (c.RESULTS_DIR / f"{args.id}.json").write_text(json.dumps(rep, indent=1), encoding="utf-8")
    write_md_report(rep, c.RESULTS_DIR / f"{args.id}.md")
    cm = rep["compare"]
    c.log(f"result: {rep['status']} (confirmed={cm['confirmedFindings']}, flaky={cm['flakyFindings']}, "
          f"lowconf={cm['lowConfidence']}, noise={cm['noise']}) -> {c.RESULTS_DIR / (args.id + '.md')}")
    return 0 if rep["status"] in ("OK", "FALLBACK-ISSUE", "DIFF") else 1


if __name__ == "__main__":
    c.main_guard(main)
