"""Download and verify the server runtime (launcher, vanilla server jar, Carpet) and build the template dir.

Sources: piston-meta/piston-data (Mojang), meta.fabricmc.net, Modrinth API/CDN.
Usage: python fetch_runtime.py [--rebuild-template]
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
import urllib.parse

import common as c

BOOT_PORT = 25598


def fetch_server_jar() -> None:
    vj = c.http_json(c.VERSION_JSON_URL)
    srv = vj["downloads"]["server"]
    c.log(f"vanilla server {c.MC_VERSION}: sha1={srv['sha1']} size={srv['size']}")
    c.download(srv["url"], c.CACHE_SERVER / f"server-{c.MC_VERSION}.jar", "sha1", srv["sha1"])


def fetch_launcher() -> None:
    # The launcher URL has no published hash; keep the first download and rely on TLS.
    dest = c.CACHE_SERVER / f"fabric-server-launch-{c.MC_VERSION}.jar"
    if dest.exists() and dest.stat().st_size > 10000:
        c.log(f"  cached: {dest.name}")
        return
    c.log(f"fabric launcher: {c.LAUNCHER_URL}")
    c.download(c.LAUNCHER_URL, dest)


def fetch_carpet() -> None:
    q = urllib.parse.urlencode({"game_versions": f'["{c.MC_VERSION}"]', "loaders": '["fabric"]'})
    versions = c.http_json(f"https://api.modrinth.com/v2/project/{c.CARPET_PROJECT}/version?{q}")
    for v in versions:
        for f in v["files"]:
            if f["filename"] == c.CARPET_FILE:
                c.log(f"carpet: {f['filename']} sha512={f['hashes']['sha512'][:16]}...")
                c.download(f["url"], c.CACHE_MODS / "carpet.jar", "sha512", f["hashes"]["sha512"])
                return
    raise RuntimeError(f"carpet file {c.CARPET_FILE} not found on Modrinth")


def build_template(rebuild: bool) -> None:
    tpl = c.CACHE_TEMPLATE
    if tpl.exists() and (tpl / ".fabric").exists() and not rebuild:
        c.log(f"template exists: {tpl}")
        return
    if tpl.exists():
        shutil.rmtree(tpl)
    tpl.mkdir(parents=True)
    shutil.copy2(c.CACHE_SERVER / f"fabric-server-launch-{c.MC_VERSION}.jar", tpl / "fabric-server-launch.jar")
    shutil.copy2(c.CACHE_SERVER / f"server-{c.MC_VERSION}.jar", tpl / "server.jar")
    (tpl / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (tpl / "server.properties").write_text(
        f"server-port={BOOT_PORT}\nserver-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft\\:flat\n"
        "view-distance=2\nsimulation-distance=2\nmax-tick-time=-1\n", encoding="ascii")
    (tpl / "fabric-server-launcher.properties").write_text("serverJar=server.jar\n", encoding="ascii")
    c.log("booting template once (fetches libraries, writes .fabric) ...")
    proc = subprocess.Popen(
        ["java", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=tpl,
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    done = False
    t0 = time.time()
    tail: list[str] = []
    try:
        for line in proc.stdout:
            tail.append(line.rstrip())
            tail = tail[-30:]
            if "Done (" in line:
                done = True
                break
            if time.time() - t0 > 600:
                break
        if done:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
            proc.wait(timeout=120)
    finally:
        if proc.poll() is None:
            proc.kill()
    if not done:
        sys.stderr.write("\n".join(tail) + "\n")
        raise RuntimeError("template boot did not reach 'Done ('")
    # Keep launcher, server jar, libraries, .fabric etc.; drop per-run state.
    for name in ("world", "logs", "server.properties", "usercache.json", "banned-players.json",
                 "banned-ips.json", "ops.json", "whitelist.json", "crash-reports", "config"):
        p = tpl / name
        if p.is_dir():
            shutil.rmtree(p)
        elif p.exists():
            p.unlink()
    c.log("template ready: " + ", ".join(sorted(p.name for p in tpl.iterdir())))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rebuild-template", action="store_true")
    args = ap.parse_args()
    c.ensure_dirs()
    fetch_server_jar()
    fetch_launcher()
    fetch_carpet()
    build_template(args.rebuild_template)
    c.log("fetch_runtime: OK")
    return 0


if __name__ == "__main__":
    c.main_guard(main)
