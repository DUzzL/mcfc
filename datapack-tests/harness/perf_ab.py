"""Perf A/B: one pack (default bacap with its scenario), solo runs, several configurations.

Configurations: vanilla (no mcfc), mcfc with jar A, mcfc with jar B. Order A B C C B A (alternating) so that
machine drift hits every configuration equally. Every run: same seed and scenario, warm-up sprint 200,
then N sprints of 1200 ticks. Writes results/_perf_ab.md and results/_perf_ab.json.

Usage: python perf_ab.py [--pack bacap] [--sprints 5] [--order vanilla,pre,shard,shard,pre,vanilla]
"""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import sys
import time
from pathlib import Path

import common as c
from run_variant import free_port

CONFIGS = {
    "vanilla": {"label": "perfbase", "jar": None},
    "pre-shard": {"label": "mcfc", "jar": c.CACHE_MODS / "mcfc-pre-shard.jar"},
    "shard": {"label": "mcfc", "jar": c.CACHE_MODS / "mcfc-shard.jar"},
}
ALIASES = {"vanilla": "vanilla", "pre": "pre-shard", "pre-shard": "pre-shard", "shard": "shard"}


def run_config(pack: str, name: str, idx: int, sprints: int, root: Path, extra: list[str]) -> dict:
    cfg = CONFIGS[name]
    run_dir = root / f"{idx}-{name}"
    port = free_port(25700)
    rport = free_port(port + 1)
    cmd = [sys.executable, str(c.HARNESS_DIR / "run_variant.py"), pack, "--label", cfg["label"],
           "--run-dir", str(run_dir), "--port", str(port), "--rcon-port", str(rport),
           "--no-exercise", "--perf", "--reps", str(sprints)] + extra
    if cfg["jar"]:
        cmd += ["--mcfc-jar", str(cfg["jar"])]
    t0 = time.time()
    with open(root / f"{idx}-{name}.stdout.log", "w", encoding="utf-8") as log:
        rc = subprocess.call(cmd, stdout=log, stderr=subprocess.STDOUT, cwd=c.HARNESS_DIR)
    info = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))
    perf = info.get("perf") or {}
    ms = [r["mspt"] for r in perf.get("runs", [])]
    res = {"index": idx, "config": name, "rc": rc, "seconds": round(time.time() - t0, 1),
           "mspt": ms, "median": statistics.median(ms) if ms else None,
           "min": min(ms) if ms else None, "max": max(ms) if ms else None,
           "lines": perf.get("lines"), "error": info.get("error"),
           "mcfcState": info.get("mcfcStateName"), "jarSha256": info.get("mcfcJarSha256")}
    c.log(f"run {idx} {name}: median {res['median']} ms (min {res['min']}, max {res['max']}) rc={rc}")
    return res


def r3(x: float) -> float:
    return round(x, 3)


def write_report(pack: str, sprints: int, results: list[dict], dest: Path) -> dict:
    by: dict[str, list[dict]] = {}
    for r in results:
        by.setdefault(r["config"], []).append(r)
    summary = {}
    for name, rs in by.items():
        allms = [m for r in rs for m in r["mspt"]]
        meds = [r["median"] for r in rs if r["median"] is not None]
        # the last sprint is much cheaper in every run (scenario state change after ~5000 ticks), so also
        # report the first sprints alone
        head = [m for r in rs for m in r["mspt"][:-1]] if sprints >= 5 else []
        summary[name] = {"medianOfRunMedians": r3(statistics.median(meds)) if meds else None,
                         "medianAllSprints": r3(statistics.median(allms)) if allms else None,
                         "min": min(allms) if allms else None, "max": max(allms) if allms else None,
                         "medianFirstSprints": r3(statistics.median(head)) if head else None,
                         "minFirstSprints": min(head) if head else None, "maxFirstSprints": max(head) if head else None,
                         "runs": len(rs), "jarSha256": next((r["jarSha256"] for r in rs if r["jarSha256"]), None)}
    L = [f"# perf A/B: {pack}", "",
         f"Solo runs (one server at a time), same seed and scenario, warm-up `tick sprint 200`, then {sprints} x "
         f"`tick sprint 1200`; ms per tick as reported by the server. Exerciser off. Order: "
         + " ".join(r["config"] for r in results) + ".", "",
         "## per run", "", "| # | config | median | min | max | sprints (ms/tick) |", "|---|---|---|---|---|---|"]
    for r in results:
        L.append(f"| {r['index']} | {r['config']} | {r['median']} | {r['min']} | {r['max']} | "
                 f"{', '.join(str(x) for x in r['mspt'])} |")
    L += ["", "## per configuration (both runs pooled)", "",
          "| config | median of run medians | median of all sprints (min/max) | median of sprints 1-4 (min/max) | jar sha256 |",
          "|---|---|---|---|---|"]
    for name, s in summary.items():
        first = (f"{s['medianFirstSprints']} ({s['minFirstSprints']}/{s['maxFirstSprints']})"
                 if s["medianFirstSprints"] is not None else "-")
        L.append(f"| {name} | {s['medianOfRunMedians']} | {s['medianAllSprints']} ({s['min']}/{s['max']}) | {first} | "
                 f"{(s['jarSha256'] or '-')[:16]} |")
    v = summary.get("vanilla", {}).get("medianFirstSprints") or summary.get("vanilla", {}).get("medianOfRunMedians")
    if v:
        L += ["", "ratio to vanilla (median of sprints 1-4):"] + [
            f"- {n}: {(s['medianFirstSprints'] or s['medianOfRunMedians']) / v:.3f}x"
            for n, s in summary.items() if n != "vanilla"]
    L += ["", "Notes: the last sprint of every run is about 2.5x cheaper than the first four (same in vanilla, so "
          "not an mcfc effect). The first vanilla run is slower than the second one (cold start), which an A B C C B A "
          "order only partly cancels."]
    bad = [r for r in results if r["error"] or r["rc"] != 0]
    if bad:
        L += ["", "failed runs: " + ", ".join(f"{r['index']}-{r['config']} ({r['error']})" for r in bad)]
    dest.write_text("\n".join(L) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default="bacap")
    ap.add_argument("--sprints", type=int, default=5)
    ap.add_argument("--order", default="vanilla,pre,shard,shard,pre,vanilla")
    args, extra = ap.parse_known_args()
    order = [ALIASES[x] for x in args.order.split(",")]
    root = c.RUNS / "_perf_ab"
    root.mkdir(parents=True, exist_ok=True)
    results = []
    for i, name in enumerate(order, 1):
        results.append(run_config(args.pack, name, i, args.sprints, root, extra))
        (c.RESULTS_DIR / "_perf_ab.json").write_text(json.dumps(results, indent=1), encoding="utf-8")
    summary = write_report(args.pack, args.sprints, results, c.RESULTS_DIR / "_perf_ab.md")
    (c.RESULTS_DIR / "_perf_ab.json").write_text(json.dumps({"runs": results, "summary": summary}, indent=1),
                                                 encoding="utf-8")
    return 0


if __name__ == "__main__":
    c.main_guard(main)
