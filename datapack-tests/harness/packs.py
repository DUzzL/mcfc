"""Pack resolution (manifest), pack_format patching and zip inspection helpers."""
from __future__ import annotations

import io
import json
import re
import zipfile
from pathlib import Path

import common as c

FUNC_DIR_RX = re.compile(r"^data/([^/]+)/functions?/(.+)\.mcfunction$")


# ---------------------------------------------------------------------------
# pack_format handling
# ---------------------------------------------------------------------------

def parse_format(value) -> tuple[int, int]:
    """'121.0' | 121 | [121, 0] -> (121, 0)."""
    if isinstance(value, (list, tuple)):
        return int(value[0]), int(value[1]) if len(value) > 1 else 0
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        s = str(value)
    else:
        s = str(value)
    if "." in s:
        a, b = s.split(".", 1)
        return int(a), int(b or 0)
    return int(s), 0


def _read_mcmeta(zf: zipfile.ZipFile) -> dict | None:
    try:
        raw = zf.read("pack.mcmeta")
    except KeyError:
        return None
    return json.loads(raw.decode("utf-8-sig"))


def _fmt_major(v) -> int | None:
    if isinstance(v, bool):
        return None
    if isinstance(v, int):
        return v
    if isinstance(v, list) and v:
        return int(v[0])
    return None


def pack_compatible(zip_path: Path, fmt: tuple[int, int]) -> bool:
    """True when pack.mcmeta already covers the given data pack format."""
    with zipfile.ZipFile(zip_path) as zf:
        meta = _read_mcmeta(zf)
    if not meta or "pack" not in meta:
        return False
    p = meta["pack"]
    lo = _fmt_major(p.get("min_format"))
    hi = _fmt_major(p.get("max_format"))
    if lo is not None and hi is not None:
        return lo <= fmt[0] <= hi
    pf = _fmt_major(p.get("pack_format"))
    return pf == fmt[0]


def patch_pack_format(src: Path, dst: Path, fmt: tuple[int, int]) -> list[str]:
    """Copy src to dst changing only the pack format fields of pack.mcmeta.

    All other entries are copied with their original ZipInfo and bytes.
    Returns a list of human-readable changes.
    """
    changes: list[str] = []
    dst.parent.mkdir(parents=True, exist_ok=True)
    major, minor = fmt
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename == "pack.mcmeta":
                meta = json.loads(data.decode("utf-8-sig"))
                p = meta.get("pack", {})

                def conv(old, new_major=major, new_minor=minor):
                    return [new_major, new_minor] if isinstance(old, list) else new_major

                if "pack_format" in p:
                    changes.append(f"pack_format {p['pack_format']!r} -> {major}")
                    p["pack_format"] = major
                for key in ("min_format", "max_format"):
                    if key in p:
                        new = conv(p[key])
                        changes.append(f"{key} {p[key]!r} -> {new!r}")
                        p[key] = new
                if "pack_format" in p and "min_format" not in p and "max_format" not in p:
                    p["min_format"] = [major, minor]
                    p["max_format"] = [major, minor]
                    changes.append(f"added min_format/max_format [{major}, {minor}] (required for formats above 81)")
                if "supported_formats" in p:
                    old = p["supported_formats"]
                    if isinstance(old, dict):
                        new = {"min_inclusive": major, "max_inclusive": major}
                    elif isinstance(old, list):
                        new = [major, major]
                    else:
                        new = major
                    changes.append(f"supported_formats {old!r} -> {new!r}")
                    p["supported_formats"] = new
                data = json.dumps(meta, indent=2, ensure_ascii=False).encode("utf-8")
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zi.create_system = info.create_system
            zout.writestr(zi, data)
    return changes


# ---------------------------------------------------------------------------
# Spec resolution
# ---------------------------------------------------------------------------

class SkippedPack(Exception):
    """Raised for manifest entries marked "skip": true."""


def resolve_spec(target: str, zips: list[str] | None = None, kind: str | None = None,
                 allow_skipped: bool = False) -> dict:
    """Build a run spec: {id, kind, packs:[{id, zip, patch}], format:[maj,min]}.

    target is a manifest pack id or combination id; with zips, target is just a label and
    the given zip files are used directly (kind defaults to 'function').
    """
    manifest = c.load_manifest() or {}
    fmt = parse_format(manifest.get("packFormat26_3", "121.0"))
    packs: list[dict] = []
    if zips:
        for z in zips:
            zp = Path(z).resolve()
            packs.append({"id": zp.stem, "zip": str(zp), "patch": False, "manifestKind": kind or "function"})
        spec_kind = kind or "function"
    else:
        by_id = {p["id"]: p for p in manifest.get("packs", [])}
        combos = {x["id"]: x for x in manifest.get("combinations", [])}
        if target in combos:
            ids = combos[target]["packs"]
        elif target in by_id:
            ids = [target]
        else:
            raise SystemExit(f"unknown manifest id: {target}")
        kinds = []
        for pid in ids:
            e = by_id[pid]
            if e.get("skip") and not allow_skipped:
                raise SkippedPack(f"{pid} is skipped: {e.get('skipReason', 'no reason given')}")
            zp = c.CACHE_PACKS / f"{pid}.zip"
            if not zp.exists():
                raise SystemExit(f"missing pack zip: {zp}")
            packs.append({"id": pid, "zip": str(zp), "patch": bool(e.get("needsPackFormatPatch")),
                          "manifestKind": e.get("kind", "function")})
            kinds.append(e.get("kind", "function"))
        spec_kind = kind or ("function" if all(k == "function" for k in kinds) else "worldgen")
    for p in packs:
        if not p["patch"] and not pack_compatible(Path(p["zip"]), fmt):
            p["patch"] = True
            p["autoPatch"] = True
    return {"id": target, "kind": spec_kind, "packs": packs, "format": list(fmt)}


def install_pack(pack: dict, fmt: tuple[int, int], datapacks_dir: Path) -> tuple[str, list[str]]:
    """Copy (or patch+copy) a pack zip into datapacks_dir. Returns (file name, notes)."""
    datapacks_dir.mkdir(parents=True, exist_ok=True)
    name = f"{pack['id']}.zip"
    dst = datapacks_dir / name
    src = Path(pack["zip"])
    if pack.get("patch"):
        changes = patch_pack_format(src, dst, fmt)
        why = "auto-detected" if pack.get("autoPatch") else "manifest"
        return name, [f"pack_format patched ({why}): " + "; ".join(changes or ["no format fields found"])]
    dst.write_bytes(src.read_bytes())
    return name, []


# ---------------------------------------------------------------------------
# Inspection
# ---------------------------------------------------------------------------

def scan_functions(zip_paths: list[str]) -> dict[str, bool]:
    """Map function id -> has_macro_lines over all given zips (later zips override)."""
    out: dict[str, bool] = {}
    for zp in zip_paths:
        with zipfile.ZipFile(zp) as zf:
            for name in zf.namelist():
                m = FUNC_DIR_RX.match(name)
                if not m or "/function/" not in name:
                    continue
                text = zf.read(name).decode("utf-8", errors="replace")
                macro = any(line.startswith("$") for line in text.splitlines())
                out[f"{m.group(1)}:{m.group(2)}"] = macro
    return out


# ---------------------------------------------------------------------------
# Generated helper packs
# ---------------------------------------------------------------------------

def _make_zip(dest: Path, files: dict[str, str]) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, text in files.items():
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zf.writestr(zi, text.encode("utf-8"))
    dest.write_bytes(buf.getvalue())
    return dest


def _mcmeta(desc: str, fmt: tuple[int, int]) -> str:
    return json.dumps({"pack": {"description": desc, "min_format": list(fmt), "max_format": list(fmt)}}, indent=2)


def build_counter_pack(dest: Path, fmt: tuple[int, int] = (121, 0)) -> Path:
    """Tiny pack whose tick function increments a score."""
    return _make_zip(dest, {
        "pack.mcmeta": _mcmeta("counter test pack", fmt),
        "data/minecraft/tags/function/load.json": json.dumps({"values": ["counter:load"]}),
        "data/minecraft/tags/function/tick.json": json.dumps({"values": ["counter:tick"]}),
        "data/counter/function/load.mcfunction":
            "scoreboard objectives add counter dummy\nscoreboard players set #ticks counter 0\n"
            "scoreboard players set #touched counter 0\n",
        "data/counter/function/tick.mcfunction":
            "scoreboard players add #ticks counter 1\n"
            "execute as @a run scoreboard players add @s counter 1\n",
        "data/counter/function/touch.mcfunction":
            "scoreboard players add #touched counter 1\n"
            "data modify storage counter:state touched set value 1b\n"
            "execute as @s at @s run setblock ~ ~-1 ~ minecraft:gold_block\n",
        "data/counter/function/macro.mcfunction": "$say $(x)\n",
    })


if __name__ == "__main__":
    c.ensure_dirs()
    out = build_counter_pack(c.CACHE_PACKS / "_counter.zip")
    print("wrote", out)
