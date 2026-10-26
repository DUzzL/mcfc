"""Compare final dumps of base1, base2 and mcfc.

noise    = paths where base1 != base2 (non-deterministic even without mcfc)
finding  = path where mcfc differs from base1 and from base2 and the path is not noise

Usage: python compare.py <run root containing base1/ base2/ mcfc/> [--dump final]
"""
from __future__ import annotations

import argparse
import json
import re
import uuid as uuidlib
from pathlib import Path

import common as c

MISSING = "<missing>"
UUID_RX = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
INTARR_RX = re.compile(r"\[I;\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\]")
MAX_FINDINGS_JSON = 3000


def _ints_to_uuid(a: int, b: int, cc: int, d: int) -> str:
    val = 0
    for x in (a, b, cc, d):
        val = (val << 32) | (x & 0xFFFFFFFF)
    return str(uuidlib.UUID(int=val))


def _find_uuids(text: str):
    """Yield canonical lowercase uuid strings in order of appearance."""
    for m in re.finditer(UUID_RX.pattern + "|" + INTARR_RX.pattern, text):
        s = m.group(0)
        if s.startswith("[I;"):
            im = INTARR_RX.match(s)
            yield _ints_to_uuid(*(int(g) for g in im.groups()))
        else:
            yield s.lower()


def _entity_sort_key(e: dict):
    pos = e.get("pos")
    return (str(e.get("type")), json.dumps(pos), str(e.get("customName")), json.dumps(e.get("tags"), sort_keys=True))


def _walk_strings(obj):
    if isinstance(obj, str):
        yield obj
    elif isinstance(obj, dict):
        for k, v in obj.items():
            yield k
            yield from _walk_strings(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from _walk_strings(v)


def _rewrite(obj, sub):
    if isinstance(obj, str):
        return sub(obj)
    if isinstance(obj, dict):
        return {sub(k): _rewrite(v, sub) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_rewrite(v, sub) for v in obj]
    return obj


def normalize(doc: dict) -> dict:
    """Drop meta, sort entities, relabel UUIDs by first appearance."""
    doc = {k: v for k, v in doc.items() if k != "meta"}
    labels: dict[str, str] = {}

    counter = [0]

    def label(u: str, prefix: str = "E") -> None:
        if u not in labels:
            counter[0] += 1
            labels[u] = f"{prefix}{counter[0]}"

    players = doc.get("players")
    if isinstance(players, dict):
        for name in sorted(players):
            info = players[name]
            if isinstance(info, dict) and isinstance(info.get("uuid"), str):
                labels[info["uuid"].lower()] = f"P:{name}"
    ents = doc.get("entities")
    if isinstance(ents, dict):
        for dim in sorted(ents):
            sec = ents[dim]
            if isinstance(sec, dict) and isinstance(sec.get("list"), list):
                sec["list"] = sorted(sec["list"], key=_entity_sort_key)
        # own uuids get a per-type index so a changed count of one type does not shift other labels
        per_type: dict[str, int] = {}
        for dim in sorted(ents):
            sec = ents[dim]
            if isinstance(sec, dict) and isinstance(sec.get("list"), list):
                for e in sec["list"]:
                    own = str(e.get("uuid", "")).lower()
                    if own and own not in labels:
                        t = str(e.get("type", "?")).replace("minecraft:", "")
                        per_type[t] = per_type.get(t, 0) + 1
                        labels[own] = f"{t}#{per_type[t]}"
        # uuids only referenced inside NBT (owners, targets) are labelled by first appearance
        for dim in sorted(ents):
            sec = ents[dim]
            if isinstance(sec, dict) and isinstance(sec.get("list"), list):
                for e in sec["list"]:
                    for s in _walk_strings(e):
                        for u in _find_uuids(s):
                            label(u, "U")

    def sub(s: str) -> str:
        def r1(m):
            return "<" + labels.get(m.group(0).lower(), "U?") + ">"

        def r2(m):
            u = _ints_to_uuid(*(int(g) for g in m.groups()))
            return "<" + labels.get(u, "U?") + ">"
        s = UUID_RX.sub(r1, s)
        return INTARR_RX.sub(r2, s)

    return _rewrite(doc, sub)


def flatten(doc: dict) -> dict[str, str]:
    out: dict[str, str] = {}

    def put(path: str, value) -> None:
        out[path] = json.dumps(value, sort_keys=True, ensure_ascii=False)

    def rec(path: str, v) -> None:
        if isinstance(v, dict):
            if not v:
                put(path + "{}", v)
            for k in sorted(v):
                rec(f"{path}.{k}" if path else k, v[k])
        elif isinstance(v, list):
            if not v:
                put(path + "[]", v)
            for i, x in enumerate(v):
                rec(f"{path}[{i}]", x)
        else:
            put(path, v)

    for sec, val in doc.items():
        if sec == "entities" and isinstance(val, dict):
            for dim in sorted(val):
                d = val[dim]
                if not isinstance(d, dict) or not isinstance(d.get("list"), list):
                    rec(f"entities.{dim}", d)
                    continue
                for k in d:
                    if k != "list":
                        rec(f"entities.{dim}.{k}", d[k])
                counter: dict[str, int] = {}
                for e in d["list"]:
                    t = str(e.get("type"))
                    counter[t] = counter.get(t, 0) + 1
                    rec(f"entities.{dim}.list[{t}#{counter[t]}]", e)
        elif sec == "blocks" and isinstance(val, list):
            for b in val:
                rec(f"blocks[{b.get('name')}]", {k: x for k, x in b.items() if k != "name"})
        else:
            rec(sec, val)
    return out


def load_dump(run_dir: Path, name: str) -> dict:
    p = run_dir / "dumps" / f"{name}.json"
    return json.loads(p.read_text(encoding="utf-8"))


def trunc(s: str, n: int = 200) -> str:
    return s if len(s) <= n else s[: n - 3] + "..."


def section_of(path: str) -> str:
    return re.split(r"[.\[{]", path, 1)[0]


LOWCONF_ENTITY_RX = re.compile(
    r"(list\[minecraft:(falling_block|item|experience_orb)#|counts\.minecraft:(falling_block|item|experience_orb)$)")
GEN_PATH_RX = re.compile(r"^(blocks\[|entities\.[^.\[]+\.counts)")
COLUMN_RX = re.compile(r"^blocks\[([^\]]+)\]\.columns\.")
BOX_AGG_RX = re.compile(r"^blocks\[([^\]]+)\]\.(hash|nonAirCount)$")


def _load_flat(run_dir: Path, dump: str) -> dict[str, str]:
    return flatten(normalize(load_dump(run_dir, dump)))


def compare_runs(root: Path, dump: str = "final", gen_only: bool = False, lowconf: bool = False,
                 extra_baselines: list[Path] | None = None) -> dict:
    """Compare one dump of base1/base2/mcfc.

    gen_only: restrict to block hashes and entity counts (worldgen check).
    extra_baselines: more vanilla run dirs; a path is noise unless all baselines agree on it.
    lowconf:  classify findings of unstable things (falling blocks, items, orbs, block columns that
              changed between the gen and final dump in a baseline) as low confidence.
    """
    result: dict = {"dump": dump, "status": "OK", "sections": {}, "findings": [], "findingsTotal": 0,
                    "findingPaths": [], "lowConfidence": [], "lowConfidenceTotal": 0,
                    "noiseTotal": 0, "noiseSample": [], "errors": []}
    dirs = {"base1": root / "base1", "base2": root / "base2", "mcfc": root / "mcfc"}
    for i, d in enumerate(extra_baselines or []):
        dirs[f"extra{i}"] = Path(d)
    docs = {}
    for label, d in dirs.items():
        try:
            docs[label] = _load_flat(d, dump)
        except Exception as e:  # noqa: BLE001
            result["errors"].append(f"{label}: {type(e).__name__}: {e}")
            if label in ("base1", "base2", "mcfc"):
                result["status"] = "INCOMPLETE"
    if result["status"] == "INCOMPLETE":
        return result
    result["baselines"] = [k for k in docs if k != "mcfc"]
    if gen_only:
        docs = {k: {p: v for p, v in d.items() if GEN_PATH_RX.match(p)} for k, d in docs.items()}
    b1, mc = docs["base1"], docs["mcfc"]
    bases = [d for k, d in docs.items() if k != "mcfc"]
    paths = set(mc).union(*bases)
    noise = {p for p in paths if any(d.get(p, MISSING) != b1.get(p, MISSING) for d in bases[1:])}
    findings = sorted(p for p in paths if p not in noise and mc.get(p, MISSING) != b1.get(p, MISSING))

    low: set[str] = set()
    if lowconf and not gen_only:
        unstable_cols: set[str] = set()
        try:
            gens = {}
            for k in docs:
                if k != "mcfc" and (dirs[k] / "dumps" / "gen.json").exists():
                    gens[k] = _load_flat(dirs[k], "gen")
            unstable_cols = {p for p in paths if COLUMN_RX.match(p)
                             and any(gens[k].get(p, MISSING) != docs[k].get(p, MISSING) for k in gens)}
        except Exception as e:  # noqa: BLE001
            result["errors"].append(f"gen dumps unavailable for low-confidence rules: {e}")
        for p in findings:
            if LOWCONF_ENTITY_RX.search(p) or p in unstable_cols:
                low.add(p)
        # the hash/count of a box is low confidence when none of its column findings is stable
        stable_boxes = {COLUMN_RX.match(p).group(1) for p in findings if COLUMN_RX.match(p) and p not in low}
        for p in findings:
            m = BOX_AGG_RX.match(p)
            if m and m.group(1) not in stable_boxes:
                low.add(p)
    high = [p for p in findings if p not in low]
    lows = [p for p in findings if p in low]

    secs: dict[str, dict] = {}
    for p in paths:
        s = secs.setdefault(section_of(p), {"paths": 0, "noise": 0, "findings": 0, "lowConfidence": 0})
        s["paths"] += 1
    for p in noise:
        secs[section_of(p)]["noise"] += 1
    for p in high:
        secs[section_of(p)]["findings"] += 1
    for p in lows:
        secs[section_of(p)]["lowConfidence"] += 1
    result["sections"] = dict(sorted(secs.items()))
    result["noiseTotal"] = len(noise)
    result["noiseSample"] = sorted(noise)[:40]

    def rec(p: str) -> dict:
        return {"path": p, "base": trunc(b1.get(p, MISSING), 500), "mcfc": trunc(mc.get(p, MISSING), 500)}

    result["findingsTotal"] = len(high)
    result["findingPaths"] = high
    result["findings"] = [rec(p) for p in high[:MAX_FINDINGS_JSON]]
    result["lowConfidenceTotal"] = len(lows)
    result["lowConfidence"] = [rec(p) for p in lows[:500]]
    if high:
        result["status"] = "DIFF"
    return result


def write_md(res: dict, dest: Path) -> None:
    lines = [f"# compare ({res['dump']}): {res['status']}", "",
             f"noise paths: {res['noiseTotal']}, findings: {res['findingsTotal']}, "
             f"low-confidence: {res.get('lowConfidenceTotal', 0)}", ""]
    if res["errors"]:
        lines += ["errors:"] + [f"- {e}" for e in res["errors"]] + [""]
    lines += ["| section | paths | noise | findings | low-conf |", "|---|---|---|---|---|"]
    for s, v in res["sections"].items():
        lines.append(f"| {s} | {v['paths']} | {v['noise']} | {v['findings']} | {v.get('lowConfidence', 0)} |")
    lines += ["", "## first findings (base1 vs mcfc)", ""]
    for f in res["findings"][:40]:
        lines.append(f"- `{f['path']}`: base={trunc(f['base'])} | mcfc={trunc(f['mcfc'])}")
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("root")
    ap.add_argument("--dump", default="final")
    ap.add_argument("--gen-only", action="store_true", help="blocks + entity counts only (use with --dump gen)")
    ap.add_argument("--lowconf", action="store_true")
    args = ap.parse_args()
    root = Path(args.root)
    res = compare_runs(root, args.dump, args.gen_only, args.lowconf)
    (root / f"compare-{args.dump}.json").write_text(json.dumps(res, indent=1), encoding="utf-8")
    write_md(res, root / f"compare-{args.dump}.md")
    print(f"compare {args.dump}: {res['status']} noise={res['noiseTotal']} findings={res['findingsTotal']} "
          f"lowconf={res['lowConfidenceTotal']}")
    return 0


if __name__ == "__main__":
    c.main_guard(main)
