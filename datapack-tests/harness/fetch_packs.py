#!/usr/bin/env python3
"""Download and validate the datapacks listed in datapack-tests/manifest.json.

Stdlib only. Packs are stored as <MCFC_WORK>/cache/packs/<id>.zip, where MCFC_WORK
defaults to C:\\Users\\duzzl\\Documents\\MCFC-work. Only official hosts are contacted:
Modrinth (api/cdn) and vanillatweaks.net.

Rules:
  * A file that already exists with a matching sha256 is reused.
  * Modrinth packs: sha256 plus sha512 and sha1 (when present in the manifest) must match.
  * Vanilla Tweaks packs are built on demand, so their sha256 may change between builds.
    A mismatch only produces a warning. An existing VT file is kept unless --refresh is given.
  * Every pack is inspected (pack.mcmeta at the zip root, data/ present, function layout)
    and the counts are compared with manifest "stats" (warning only).

Usage:
  python fetch_packs.py [--only ID [ID ...]] [--refresh] [--manifest PATH]

Exit code 0 when all selected packs are present and valid, 1 otherwise.
"""
import argparse
import hashlib
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

USER_AGENT = "mcfc-datapack-tests/1.0"
ALLOWED_HOSTS = {
    "api.modrinth.com",
    "cdn.modrinth.com",
    "vanillatweaks.net",
    "www.vanillatweaks.net",
}
DEFAULT_WORK = r"C:\Users\duzzl\Documents\MCFC-work"
HERE = Path(__file__).resolve().parent
DEFAULT_MANIFEST = HERE.parent / "manifest.json"
VT_INNER_RE = re.compile(r"^(?P<name>.+) v(?P<version>[0-9][0-9A-Za-z.\-]*) \(MC (?P<mc>[0-9.]+)\)\.zip$")


def work_dir():
    return Path(os.environ.get("MCFC_WORK", DEFAULT_WORK))


def packs_dir():
    return work_dir() / "cache" / "packs"


class _HostCheckRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        check_host(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


_OPENER = urllib.request.build_opener(_HostCheckRedirect)


def check_host(url):
    host = urllib.parse.urlparse(url).hostname
    if urllib.parse.urlparse(url).scheme != "https" or host not in ALLOWED_HOSTS:
        raise RuntimeError("refusing to contact non-allowed URL: " + url)


def http(url, data=None, headers=None, timeout=120, retries=3):
    check_host(url)
    hdrs = {"User-Agent": USER_AGENT}
    hdrs.update(headers or {})
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, data=data, headers=hdrs)
            with _OPENER.open(req, timeout=timeout) as resp:
                return resp.read()
        except (urllib.error.URLError, TimeoutError, ConnectionError) as exc:
            last = exc
            if isinstance(exc, urllib.error.HTTPError) and 400 <= exc.code < 500 and exc.code != 429:
                break
            time.sleep(2 * (attempt + 1))
    raise RuntimeError("request failed for %s: %s" % (url, last))


def digest(data, algo):
    return hashlib.new(algo, data).hexdigest()


def inspect_zip(data):
    """Return structural stats of a datapack zip held in memory."""
    zf = zipfile.ZipFile(io.BytesIO(data))
    names = zf.namelist()
    new_re = re.compile(r"^data/[^/]+/function/.+\.mcfunction$")
    old_re = re.compile(r"^data/[^/]+/functions/.+\.mcfunction$")
    new = [n for n in names if new_re.match(n)]
    old = [n for n in names if old_re.match(n)]
    other = [n for n in names if n.endswith(".mcfunction") and not new_re.match(n) and not old_re.match(n)]
    macro_lines = 0
    for name in new + old + other:
        text = zf.read(name).decode("utf-8", "replace")
        macro_lines += sum(1 for line in text.splitlines() if line.lstrip().startswith("$"))
    has_meta = "pack.mcmeta" in names
    pack_meta = None
    if has_meta:
        try:
            meta = json.loads(zf.read("pack.mcmeta").decode("utf-8-sig"))
            pack = meta.get("pack") or {}
            pack_meta = {k: v for k, v in pack.items()
                         if k in ("pack_format", "min_format", "max_format", "supported_formats")}
            if "overlays" in meta:
                pack_meta["overlays"] = True
        except (ValueError, UnicodeDecodeError) as exc:
            pack_meta = {"error": str(exc)}
    return {
        "hasPackMcmeta": has_meta,
        "hasData": any(n.startswith("data/") for n in names),
        "functions": len(new),
        "legacyFunctions": len(old),
        "overlayFunctions": len(other),
        "macroLines": macro_lines,
        "packMeta": pack_meta,
    }


def validate(entry, data, warnings):
    """Return a list of error strings; append non-fatal findings to warnings."""
    errors = []
    try:
        st = inspect_zip(data)
    except zipfile.BadZipFile:
        return ["not a valid zip"]
    if not st["hasPackMcmeta"]:
        errors.append("no pack.mcmeta at zip root")
    if not st["hasData"]:
        errors.append("no data/ directory")
    if st["legacyFunctions"] and not st["functions"]:
        errors.append("only legacy data/*/functions/ layout (will not load on 26.3)")
    elif st["legacyFunctions"]:
        warnings.append("%s: %d functions in legacy functions/ dir are ignored" % (entry["id"], st["legacyFunctions"]))
    want = entry.get("stats")
    if want:
        for key in ("functions", "macroLines"):
            if key in want and want[key] != st[key]:
                warnings.append("%s: stats.%s manifest=%s actual=%s" % (entry["id"], key, want[key], st[key]))
        if "packMeta" in want and want["packMeta"] != st["packMeta"]:
            warnings.append("%s: stats.packMeta differs from manifest" % entry["id"])
    return errors


def check_hashes(entry, data):
    """Return a list of mismatch descriptions for the hashes the manifest provides."""
    bad = []
    for field, algo in (("sha256", "sha256"), ("sha512", "sha512"), ("sha1", "sha1")):
        want = entry.get(field)
        if want and digest(data, algo) != want.lower():
            bad.append(field)
    return bad


def write_atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".part")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def fetch_modrinth(entry, refresh, warnings):
    path = packs_dir() / (entry["id"] + ".zip")
    if path.exists() and not refresh:
        data = path.read_bytes()
        if not check_hashes(entry, data):
            return path, data, "cached"
    data = http(entry["url"])
    bad = check_hashes(entry, data)
    if bad:
        raise RuntimeError("%s: hash mismatch (%s) for %s" % (entry["id"], ",".join(bad), entry["url"]))
    write_atomic(path, data)
    return path, data, "downloaded"


def build_vt(version, entries):
    """POST one request for all entries of the given VT version; return {pack name: (inner zip bytes, pack version)}."""
    selection = {}
    for e in entries:
        selection.setdefault(e["vtCategory"], []).append(e["vtPack"])
    body = urllib.parse.urlencode({"packs": json.dumps(selection), "version": version}).encode()
    resp = http(
        "https://vanillatweaks.net/assets/server/zipdatapacks.php",
        data=body,
        headers={
            "Referer": "https://vanillatweaks.net/picker/datapacks/",
            "X-Requested-With": "XMLHttpRequest",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    answer = json.loads(resp.decode("utf-8"))
    if answer.get("status") != "success":
        raise RuntimeError("Vanilla Tweaks build failed: %s" % answer)
    link = urllib.parse.urljoin("https://vanillatweaks.net/", answer["link"])
    outer = zipfile.ZipFile(io.BytesIO(http(link)))
    result = {}
    for name in outer.namelist():
        match = VT_INNER_RE.match(name)
        if match:
            result[match.group("name")] = (outer.read(name), match.group("version"), match.group("mc"))
    return result


def fetch_vanillatweaks(entries, refresh, warnings):
    """Return {id: (path, data, status)} for all VT entries."""
    out = {}
    missing = []
    for e in entries:
        path = packs_dir() / (e["id"] + ".zip")
        if path.exists() and not refresh:
            data = path.read_bytes()
            if e.get("sha256") and digest(data, "sha256") != e["sha256"].lower():
                warnings.append("%s: cached VT file sha256 differs from manifest (VT builds on demand); keeping file" % e["id"])
            out[e["id"]] = (path, data, "cached")
        else:
            missing.append(e)
    by_version = {}
    for e in missing:
        by_version.setdefault(e.get("vtMcVersion", "26.3"), []).append(e)
    for version, group in by_version.items():
        built = build_vt(version, group)
        for e in group:
            hit = built.get(e["vtPack"])
            if hit is None:
                raise RuntimeError("%s: pack '%s' missing in Vanilla Tweaks answer" % (e["id"], e["vtPack"]))
            data, pack_version, mc = hit
            if mc != version:
                warnings.append("%s: VT returned MC %s, expected %s" % (e["id"], mc, version))
            if e.get("version") and e["version"] != pack_version:
                warnings.append("%s: VT pack version %s, manifest %s" % (e["id"], pack_version, e["version"]))
            if e.get("sha256") and digest(data, "sha256") != e["sha256"].lower():
                warnings.append("%s: VT sha256 differs from manifest (builds on demand)" % e["id"])
            path = packs_dir() / (e["id"] + ".zip")
            write_atomic(path, data)
            out[e["id"]] = (path, data, "downloaded")
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--manifest", default=str(DEFAULT_MANIFEST))
    ap.add_argument("--only", nargs="+", help="restrict to these pack ids")
    ap.add_argument("--refresh", action="store_true", help="ignore cached files and download again")
    args = ap.parse_args(argv)

    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    entries = manifest["packs"]
    if args.only:
        wanted = set(args.only)
        unknown = wanted - {e["id"] for e in entries}
        if unknown:
            print("unknown ids: " + ", ".join(sorted(unknown)), file=sys.stderr)
            return 1
        entries = [e for e in entries if e["id"] in wanted]

    warnings = []
    failures = []
    results = {}

    for e in entries:
        if e["source"] != "modrinth":
            continue
        try:
            results[e["id"]] = fetch_modrinth(e, args.refresh, warnings)
        except Exception as exc:  # report and continue with the other packs
            failures.append("%s: %s" % (e["id"], exc))

    vt_entries = [e for e in entries if e["source"] == "vanillatweaks"]
    if vt_entries:
        try:
            results.update(fetch_vanillatweaks(vt_entries, args.refresh, warnings))
        except Exception as exc:
            failures.append("vanillatweaks: %s" % exc)

    for e in entries:
        res = results.get(e["id"])
        if res is None:
            continue
        path, data, status = res
        errors = validate(e, data, warnings)
        if errors:
            failures.append("%s: %s" % (e["id"], "; ".join(errors)))
            status += ", INVALID"
        print("%-34s %-10s %9d bytes  %s" % (e["id"], status, len(data), digest(data, "sha256")[:12]))

    for w in warnings:
        print("WARN " + w, file=sys.stderr)
    for f in failures:
        print("FAIL " + f, file=sys.stderr)
    print("%d pack(s) ok, %d warning(s), %d failure(s)" % (len(results) - len([f for f in failures if f.split(":")[0] in results]), len(warnings), len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
