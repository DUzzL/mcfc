"""Diagnostics-only pass: one mcfc server per pack/combination, no exerciser, no compare.

Starts the server with the pack installed like the mcfc variant, waits for a settled mcfc state, copies
generated/diagnostics.tsv to WORK/diag/<id>.tsv and stops. At most 2 servers at a time.

Usage: python diag_pass.py [--jar PATH] [--filter REGEX] [--jobs 2] [--no-combos]
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import common as c
from run_pack import all_ids
from run_variant import free_port

DIAG_DIR = c.WORK / "diag"


def run_one(pack_id: str, jar: str, slot: int) -> dict:
    run_dir = DIAG_DIR / "runs" / pack_id
    out = DIAG_DIR / f"{pack_id}.tsv"
    out.unlink(missing_ok=True)
    port = free_port(25800 + slot * 20)
    rport = free_port(port + 1)
    cmd = [sys.executable, str(c.HARNESS_DIR / "run_variant.py"), pack_id, "--label", "mcfc", "--diag-only",
           "--run-dir", str(run_dir), "--port", str(port), "--rcon-port", str(rport),
           "--mcfc-jar", jar, "--diag-out", str(out)]
    DIAG_DIR.mkdir(parents=True, exist_ok=True)
    t0 = time.time()
    with open(DIAG_DIR / f"{pack_id}.stdout.log", "w", encoding="utf-8") as log:
        rc = subprocess.call(cmd, stdout=log, stderr=subprocess.STDOUT, cwd=c.HARNESS_DIR)
    info = {}
    try:
        info = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))
    except Exception:  # noqa: BLE001
        pass
    res = {"id": pack_id, "rc": rc, "seconds": round(time.time() - t0, 1), "state": info.get("mcfcStateName"),
           "error": info.get("error"), "tsv": out.exists(), "lines": info.get("diagnosticsLines")}
    c.log(f"{pack_id}: {res}")
    return res


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=str(c.CACHE_MODS / "mcfc-cd77d43f.jar"))
    ap.add_argument("--filter")
    ap.add_argument("--jobs", type=int, default=2)
    ap.add_argument("--no-combos", action="store_true")
    args = ap.parse_args()
    ids = all_ids(not args.no_combos, args.filter)
    c.log(f"{len(ids)} entries, jar {args.jar}")
    slots = list(range(args.jobs))
    results = []
    # simple slot scheduler: each worker thread owns one slot (port range)
    queue = list(ids)

    def worker(slot: int):
        out = []
        while True:
            try:
                pid = queue.pop(0)
            except IndexError:
                return out
            out.append(run_one(pid, args.jar, slot))

    with ThreadPoolExecutor(max_workers=args.jobs) as ex:
        for r in ex.map(worker, slots):
            results += r
    DIAG_DIR.mkdir(parents=True, exist_ok=True)
    (DIAG_DIR / "_pass.json").write_text(json.dumps(results, indent=1), encoding="utf-8")
    bad = [r for r in results if not r["tsv"] or r["error"]]
    c.log(f"done: {len(results)} runs, {len(bad)} without tsv or with error: {[r['id'] for r in bad]}")
    return 0


if __name__ == "__main__":
    c.main_guard(main)
