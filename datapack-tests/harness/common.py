"""Shared paths and helpers for the differential datapack test harness (stdlib only)."""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import sys
import urllib.request
from pathlib import Path

MC_VERSION = "26.3"
FABRIC_LOADER = "0.19.5"
FABRIC_INSTALLER = "1.1.2"
VERSION_JSON_URL = ("https://piston-meta.mojang.com/v1/packages/"
                    "61fff1316bd50312624a858bbb5eb18fa45208ed/26.3.json")
LAUNCHER_URL = (f"https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}/"
                f"{FABRIC_LOADER}/{FABRIC_INSTALLER}/server/jar")
CARPET_PROJECT = "carpet"
CARPET_FILE = "fabric-carpet-26.3+v260915.jar"
USER_AGENT = "mcfc-datapack-harness/0.1 (local differential testing)"

HARNESS_DIR = Path(__file__).resolve().parent
TESTS_DIR = HARNESS_DIR.parent                      # datapack-tests
REPO_DIR = TESTS_DIR.parent                         # MCFC repo root
MANIFEST = TESTS_DIR / "manifest.json"
SCENARIOS_DIR = TESTS_DIR / "scenarios"
RESULTS_DIR = TESTS_DIR / "results"
MCFC_LIBS = REPO_DIR / "mc-26-1-fabric" / "build" / "libs"

WORK = Path(os.environ.get("MCFC_WORK") or r"C:\Users\duzzl\Documents\MCFC-work")
CACHE = WORK / "cache"
CACHE_SERVER = CACHE / "server"
CACHE_MODS = CACHE / "mods"
CACHE_PACKS = CACHE / "packs"
CACHE_TEMPLATE = CACHE / "template"
RUNS = WORK / "runs"


def log(msg: str) -> None:
    print(msg, flush=True)


def ensure_dirs() -> None:
    for d in (CACHE_SERVER, CACHE_MODS, CACHE_PACKS, CACHE_TEMPLATE.parent, RUNS):
        d.mkdir(parents=True, exist_ok=True)


def http_get(url: str, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def http_json(url: str):
    return json.loads(http_get(url).decode("utf-8"))


def file_hash(path: Path, algo: str) -> str:
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path, algo: str | None = None, expected: str | None = None) -> None:
    """Download url to dest (via a temp file); verify the hash when given."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and algo and expected and file_hash(dest, algo) == expected:
        log(f"  cached: {dest.name}")
        return
    tmp = dest.with_suffix(dest.suffix + ".part")
    log(f"  downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    if algo and expected:
        got = file_hash(tmp, algo)
        if got != expected:
            tmp.unlink(missing_ok=True)
            raise RuntimeError(f"{algo} mismatch for {dest.name}: expected {expected}, got {got}")
    os.replace(tmp, dest)


def newest_mcfc_jar() -> Path | None:
    jars = [p for p in MCFC_LIBS.glob("mcfc-*+26.3-fabric.jar")
            if not p.name.endswith(("-sources.jar", "-dev.jar"))]
    if not jars:
        return None
    return max(jars, key=lambda p: p.stat().st_mtime)


def load_manifest() -> dict | None:
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text(encoding="utf-8"))
    return None


def main_guard(fn):
    try:
        sys.exit(fn())
    except KeyboardInterrupt:
        sys.exit(130)
