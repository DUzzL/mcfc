"""Splits the server-thread samples of JFR recordings (perf_ab.py --jfr, run_variant.py --jfr) by tick phase.

Every `jdk.ExecutionSample` of the "Server thread" goes to the first matching category: functions (function tick,
commands, mcfc), entities (entity ticking), chunks, advancements, other.

Usage: python jfr_split.py <file.jfr> [...]
"""
from __future__ import annotations

import collections
import re
import subprocess
import sys

CATEGORIES = [
    ("functions", re.compile(r"ServerFunctionManager\.tick|minecraftfunctioncompiler|net\.minecraft\.commands\.")),
    ("entities", re.compile(r"ServerLevel\.tickNonPassenger|ServerLevel\.lambda\$tick")),
    ("chunks", re.compile(r"ServerChunkCache\.tick|ChunkMap")),
    ("advancements", re.compile(r"advancements\.|PlayerAdvancements|CriterionTrigger")),
]


def split(path: str) -> tuple[int, collections.Counter]:
    out = subprocess.run(["jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", "200", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace", check=True).stdout
    counts: collections.Counter = collections.Counter()
    total = 0
    for event in out.split("jdk.ExecutionSample {")[1:]:
        if 'sampledThread = "Server thread"' not in event:
            continue
        total += 1
        for name, rx in CATEGORIES:
            if rx.search(event):
                counts[name] += 1
                break
        else:
            counts["other"] += 1
    return total, counts


def main() -> int:
    for path in sys.argv[1:]:
        total, counts = split(path)
        parts = ", ".join(f"{k} {v} ({100 * v / total:.1f}%)" for k, v in counts.most_common()) if total else "-"
        print(f"{path}: {total} samples: {parts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
