"""One server run: a single variant (base1 / base2 / mcfc) of one pack spec.

Usage:
  python run_variant.py <id> --label base1|base2|mcfc [--run-dir DIR] [--zip PATH ...]
                        [--kind function|worldgen] [--ticks 200] [--perf] [--reps 3]
"""
from __future__ import annotations

import argparse
import datetime
import json
import re
import shutil
import socket
import statistics
import sys
import time
import traceback
from pathlib import Path

import common as c
import packs as pk
from mcserver import RconError, Server

SKIP_RX = re.compile(r"(uninstall|disable|reset|clear|remove_all|delete|kill_all|wipe)", re.I)
PLAYERS = [("DPTester1", 0), ("DPTester2", 3)]

# logical rule -> candidate names in preference order (26.3 uses snake_case registry ids)
RULES = {
    "command_chain_limit": (["max_command_sequence_length", "max_command_chain_length", "maxCommandChainLength"], "2147483647"),
    "spawn_mobs": (["spawn_mobs", "do_mob_spawning", "doMobSpawning"], "false"),
    "spawn_monsters": (["spawn_monsters"], "false"),
    "spawn_patrols": (["spawn_patrols"], "false"),
    "spawn_wandering_traders": (["spawn_wandering_traders"], "false"),
    "spawn_phantoms": (["spawn_phantoms"], "false"),
    "spawn_wardens": (["spawn_wardens"], "false"),
    "random_tick_speed": (["random_tick_speed"], "0"),
    "weather_cycle": (["advance_weather", "do_weather_cycle", "doWeatherCycle"], "false"),
}


def free_port(start: int) -> int:
    p = start
    while True:
        with socket.socket() as s:
            try:
                s.bind(("127.0.0.1", p))
                return p
            except OSError:
                p += 1


def default_boxes(kind: str) -> tuple[list[dict], bool]:
    if kind == "function":
        return [{"name": "main", "dimension": "minecraft:overworld", "from": [-128, -64, -128], "to": [127, 128, 127]}], False
    return [
        {"name": "overworld", "dimension": "minecraft:overworld", "from": [-128, -64, -128], "to": [127, 319, 127]},
        {"name": "nether", "dimension": "minecraft:the_nether", "from": [-64, 0, -64], "to": [63, 127, 63]},
        {"name": "end", "dimension": "minecraft:the_end", "from": [-64, 0, -64], "to": [63, 127, 63]},
    ], True


class Runner:
    def __init__(self, args):
        self.args = args
        self.label = args.label
        self.is_mcfc = args.label == "mcfc"
        self.scenario_data = {"steps": [], "exerciserSkip": []}
        self.spec = pk.resolve_spec(args.id, args.zip, args.kind)
        self.fmt = tuple(self.spec["format"])
        self.run_dir = Path(args.run_dir) if args.run_dir else c.RUNS / args.id / args.label
        self.info: dict = {
            "id": args.id, "variant": args.label, "mcfc": self.is_mcfc,
            "kind": self.spec["kind"], "packs": [p["id"] for p in self.spec["packs"]],
            "seed": args.seed, "notes": [], "phases": {}, "crash": False,
        }
        self.server: Server | None = None
        self._t = time.time()

    # -- utilities -------------------------------------------------------
    def phase(self, name: str) -> None:
        now = time.time()
        self.info["phases"][name] = round(now - self._t, 2)
        self._t = now
        c.log(f"[{self.label}] {name} ({self.info['phases'][name]}s)")

    def note(self, text: str) -> None:
        self.info["notes"].append(text)
        c.log(f"[{self.label}] note: {text}")

    def dump(self, name: str) -> Path:
        s = self.server
        reply = s.rcon.command(f"dpdump write {name}", timeout=1800)
        path = self.run_dir / "dumps" / f"{name}.json"
        if "dpdump: wrote" not in reply or not path.exists():
            raise RuntimeError(f"dpdump write {name} failed: {reply!r}")
        return path

    # -- steps -----------------------------------------------------------
    def setup(self) -> None:
        a = self.args
        kind = self.spec["kind"]
        level_type = "minecraft:flat" if kind == "function" else "minecraft:normal"
        port = a.port or free_port(25600)
        rport = a.rcon_port or free_port(port + 1 if a.port else 25700)
        self.server = Server(self.run_dir, self.label, port, rport, xmx=a.xmx, seed=str(a.seed), level_type=level_type)
        mods = [c.CACHE_MODS / "carpet.jar", c.CACHE_MODS / "dpdump.jar"]
        if self.is_mcfc:
            jar = c.newest_mcfc_jar()
            if jar is None:
                raise SystemExit("mcfc jar not found")
            mods.append(jar)
            self.info["mcfcJar"] = jar.name
            self.info["mcfcJarMtime"] = datetime.datetime.fromtimestamp(jar.stat().st_mtime).strftime("%Y-%m-%d %H:%M:%S")
        for m in mods:
            if not m.exists():
                raise SystemExit(f"missing mod: {m}")
        # structures (villages, ...) add timing-dependent noise to flat worlds
        self.server.prepare(mods, {"generate-structures": "false"} if kind == "function" else {})
        boxes, gen = default_boxes(kind)
        cfg = {"boxes": boxes, "maxEntities": 3000, "generateMissingChunks": gen}
        if a.boxes_file:
            cfg = json.loads(Path(a.boxes_file).read_text(encoding="utf-8"))
        (self.run_dir / "dpdump.json").write_text(json.dumps(cfg, indent=2), encoding="utf-8")
        dpdir = self.server.world_dir / "datapacks"
        dpdir.mkdir(parents=True, exist_ok=True)
        self.pack_files: list[str] = []
        if kind != "function":
            self.install_packs(dpdir)

    def install_packs(self, dpdir: Path) -> None:
        for p in self.spec["packs"]:
            name, notes = pk.install_pack(p, self.fmt, dpdir)
            self.pack_files.append(name)
            for n in notes:
                self.note(f"{p['id']}: {n}")

    def start(self) -> None:
        s = self.server
        s.start()
        self.info["earlyFreezeReply"] = s.early_freeze().strip()
        self.info["startupSeconds"] = round(s.wait_ready(), 1)
        self.phase("startup")

    def configure(self) -> None:
        s = self.server
        used: dict = {}
        try:
            r = s.cmd("tick freeze")
        except RconError as e:
            r = str(e)
        self.info["freezeReply"] = r
        self.info["gameTimeAtConfigure"] = s.gametime()
        for logical, (cands, value) in RULES.items():
            for name in cands:
                q = s.cmd(f"gamerule {name}")
                if "currently set to" in q:
                    r = s.cmd(f"gamerule {name} {value}")
                    used[logical] = {"name": name, "value": value, "reply": r.strip()}
                    break
            else:
                used[logical] = {"name": None, "value": value, "reply": "no matching rule"}
                self.note(f"gamerule for {logical} not found (tried {cands})")
        self.info["gamerules"] = used
        s.cmd("tick freeze")
        self.info["tickQueryAfterFreeze"] = s.cmd("tick query").strip().splitlines()[0]
        self.phase("configure")

    def load_area(self) -> None:
        """Force-load the dump boxes (entities are only dumped from loaded chunks) and wait for them."""
        s = self.server
        cfg = json.loads((self.run_dir / "dpdump.json").read_text(encoding="utf-8"))
        for b in cfg["boxes"]:
            x1, _, z1 = b["from"]
            x2, _, z2 = b["to"]
            r = s.cmd(f"execute in {b['dimension']} run forceload add {x1} {z1} {x2} {z2}")
            self.note(f"forceload {b['name']}: {r.strip()[:80]}")
            t0 = time.time()
            while time.time() - t0 < 600:
                chk = s.cmd(f"execute in {b['dimension']} if loaded {x2} 64 {z2}")
                if "passed" in chk.lower():
                    break
                time.sleep(0.5)
                if time.time() - t0 > 30 and int(time.time() - t0) % 10 == 0:
                    s.tick_step(1)  # chunk futures may need a tick when frozen
            else:
                raise TimeoutError(f"area {b['name']} did not load")
        self.dump("warm")  # first dump generates missing chunks
        if self.spec["kind"] != "function":
            self.dump("gen")  # untouched worldgen result, authoritative for worldgen comparison
        # Mob AI draws from unseeded randomness; freeze existing mobs so they do not add noise.
        r = s.cmd("execute as @e[type=!minecraft:player] run data merge entity @s {NoAI:1b}")
        self.info["noAiReply"] = r.strip()[:120]
        self.phase("load_area")

    def spawn_players(self) -> None:
        s = self.server
        for name, x in PLAYERS:
            s.cmd(f"execute in minecraft:overworld positioned {x} 0 0 positioned over motion_blocking run "
                  f"player {name} spawn at ~ ~ ~ facing 0 0 in minecraft:overworld in survival")
        t0 = time.time()
        while time.time() - t0 < 90:
            lst = s.cmd("list")
            if all(n in lst for n, _ in PLAYERS):
                break
            time.sleep(0.5)
        else:
            raise RuntimeError(f"fake players did not join: {lst!r}")
        pos = {n: s.cmd(f"data get entity {n} Pos").strip() for n, _ in PLAYERS}
        self.info["playerPos"] = pos
        self.phase("players")

    def load_packs(self) -> None:
        s = self.server
        if self.spec["kind"] == "function":
            dpdir = self.server.world_dir / "datapacks"
            self.install_packs(dpdir)
            last = f"file/{self.pack_files[-1]}"
            ok = s.reload_and_wait(last)
            if not ok:
                self.note("reload did not enable packs by itself; using /datapack enable")
        enabled = s.enabled_packs()
        for f in self.pack_files:
            tag = f"file/{f}"
            if tag not in enabled:
                if self.spec["kind"] != "function":
                    self.note(f"{f} not enabled after world creation")
                r = s.cmd(f'datapack enable "{tag}"')
                self.note(f"datapack enable {tag}: {r.strip()[:120]}")
                s.reload_and_wait(None)
        enabled = s.enabled_packs()
        self.info["enabledPacks"] = enabled
        missing = [f for f in self.pack_files if f"file/{f}" not in enabled]
        if missing:
            self.note(f"packs NOT enabled: {missing}")
            self.info["packsNotEnabled"] = missing
        self.phase("load_packs")
        if self.is_mcfc:
            s.cmd("mcfc debug on")
            t0 = time.time()
            st = s.wait_mcfc_ready(timeout=self.args.mcfc_timeout)
            self.info["mcfcState"] = st
            m = s.MCFC_STATE_RX.search(st)
            self.info["mcfcStateName"] = m.group(1) if m else "UNKNOWN"
            if self.info["mcfcStateName"] != "READY_ON":
                self.note(f"mcfc state is {self.info['mcfcStateName']}, not READY_ON (functions run through vanilla)")
            self.info["mcfcReadySeconds"] = round(time.time() - t0, 1)
            self.phase("mcfc_ready")
        r = s.cmd("function #minecraft:load")
        self.info["loadReply"] = r.strip()[:200]
        # The load tag of the reload is queued for the next tick while frozen; flush it now.
        s.tick_step(1)
        self.phase("function_load")

    def load_scenario(self) -> None:
        """scenarios/<id>.json is a list of steps or {"steps": [...], "exerciserSkip": [regex]}."""
        self.scenario_data = {"steps": [], "exerciserSkip": []}
        path = c.SCENARIOS_DIR / f"{self.args.id}.json"
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
            if isinstance(data, list):
                data = {"steps": data}
            self.scenario_data.update({k: data.get(k, []) for k in ("steps", "exerciserSkip")})

    def scenario(self) -> None:
        if not self.scenario_data["steps"]:
            return
        steps = self.scenario_data["steps"]
        log = []
        for st in steps:
            if "cmd" in st:
                log.append({"cmd": st["cmd"], "reply": self.server.cmd(st["cmd"]).strip()[:300]})
            elif "step" in st:
                self.server.tick_step(int(st["step"]))
                log.append({"step": st["step"]})
            elif "dump" in st:
                self.dump(st["dump"])
                log.append({"dump": st["dump"]})
        (self.run_dir / "scenario.json").write_text(json.dumps(log, indent=1), encoding="utf-8")
        self.info["scenarioSteps"] = len(steps)
        self.phase("scenario")

    def exercise(self) -> None:
        s = self.server
        a = self.args
        zips = [p["zip"] for p in self.spec["packs"]]
        scan = pk.scan_functions(zips)
        namespaces = {i.split(":", 1)[0] for i in scan}
        extra_skip = [re.compile(x, re.I) for x in self.scenario_data["exerciserSkip"]]
        pre = json.loads(self.dump("pre").read_text(encoding="utf-8"))
        funcs = pre.get("functions")
        if not isinstance(funcs, list):
            self.note(f"cannot read functions from dump: {str(funcs)[:100]}")
            funcs = []
        stats = {"namespaces": sorted(namespaces), "dumpedFunctions": len(funcs), "candidates": 0,
                 "skippedMacro": 0, "skippedName": 0, "skippedScenario": 0, "ran": 0, "replyExecuted": 0,
                 "replyError": 0, "hang": False, "slow": [], "budgetExceeded": False, "notRun": 0,
                 "budgetSeconds": a.exercise_budget, "cmdTimeoutSeconds": a.cmd_timeout}
        todo = []
        # the skip rules are static (regex / macro lines), so every variant gets the same list
        for fid in sorted(funcs):
            ns = fid.split(":", 1)[0]
            if ns not in namespaces:
                continue
            stats["candidates"] += 1
            if scan.get(fid):
                stats["skippedMacro"] += 1
            elif SKIP_RX.search(fid):
                stats["skippedName"] += 1
            elif any(rx.search(fid) for rx in extra_skip):
                stats["skippedScenario"] += 1
            else:
                todo.append(fid)
        if a.max_functions:
            todo = todo[: a.max_functions]
        out = open(self.run_dir / "exercise.jsonl", "w", encoding="utf-8")
        t_ex = time.time()
        try:
            for i, fid in enumerate(todo, 1):
                if time.time() - t_ex > a.exercise_budget:
                    stats["budgetExceeded"] = True
                    stats["notRun"] = len(todo) - i + 1
                    self.note(f"exerciser budget of {a.exercise_budget:.0f}s used up after {stats['ran']} of {len(todo)} functions")
                    break
                t_cmd = time.time()
                try:
                    r = s.rcon.command(f"execute as DPTester1 at @s run function {fid}", timeout=a.cmd_timeout)
                except RconError as e:
                    if "timeout" not in str(e):
                        raise
                    stats["slow"].append({"id": fid, "secondsAtTimeout": round(time.time() - t_cmd, 1)})
                    self.note(f"slow function {fid} (> {a.cmd_timeout:.0f}s); waiting for the server")
                    try:
                        s.gametime(timeout=900.0)  # blocks until the server thread is free again
                    except RconError:
                        stats["hang"] = True
                        self.note(f"server did not recover after {fid}; aborting exerciser")
                        self.info["crash"] = True
                        break
                    stats["slow"][-1]["recoverySeconds"] = round(time.time() - t_cmd, 1)
                    stats["ran"] += 1
                    out.write(json.dumps({"id": fid, "reply": "<timeout>"}) + "\n")
                    continue
                stats["ran"] += 1
                if r.startswith(("Executed", "Running function")):
                    stats["replyExecuted"] += 1
                else:
                    stats["replyError"] += 1
                out.write(json.dumps({"id": fid, "reply": r.strip()[:160]}) + "\n")
                if i % 50 == 0:
                    s.tick_step(1)
        finally:
            out.close()
        self.info["exercise"] = stats
        self.phase("exercise")

    def finish_sim(self) -> None:
        s = self.server
        s.tick_step(self.args.ticks)
        self.phase("tick_step")
        self.dump("final")
        self.phase("dump_final")
        if self.is_mcfc:
            s.cmd("mcfc debug flush")
            s.cmd("mcfc debug off")
            try:
                self.info["mcfcStateFinal"] = s.mcfc_state()
                m = s.MCFC_STATE_RX.search(self.info["mcfcStateFinal"])
                self.info["mcfcStateFinalName"] = m.group(1) if m else "UNKNOWN"
            except Exception:
                pass

    def perf(self) -> None:
        s = self.server
        rts = self.info.get("gamerules", {}).get("random_tick_speed", {}).get("name")
        if rts:
            s.cmd(f"gamerule {rts} 3")  # restore the default for realistic load
        s.cmd("tick unfreeze")
        s.sprint(200)
        res = []
        lines = []
        for _ in range(self.args.reps):
            tps, mspt, line = s.sprint(1200)
            res.append({"tps": tps, "mspt": mspt})
            lines.append(line.split("System chat: ")[-1])
        self.info["perf"] = {
            "runs": res, "lines": lines,
            "medianMspt": statistics.median(r["mspt"] for r in res),
            "medianTps": statistics.median(r["tps"] for r in res),
            "minMspt": min(r["mspt"] for r in res), "maxMspt": max(r["mspt"] for r in res),
            "note": "warmup sprint 200, then sprint 1200 x reps",
        }
        self.phase("perf")

    def shutdown(self) -> None:
        s = self.server
        code = s.stop()
        self.info["exitCode"] = code
        self.phase("stop")

    def check_crash(self) -> None:
        cr = self.run_dir / "crash-reports"
        if cr.exists() and any(cr.iterdir()):
            self.info["crash"] = True
            self.info["crashReports"] = sorted(p.name for p in cr.iterdir())

    # -- driver ----------------------------------------------------------
    def run(self) -> int:
        t_start = time.time()
        rc = 0
        try:
            self.load_scenario()
            self.setup()
            self.phase("setup")
            self.start()
            self.configure()
            self.load_area()
            self.spawn_players()
            self.load_packs()
            self.scenario()
            if not self.args.no_exercise:
                self.exercise()
            if not self.info["crash"]:
                self.finish_sim()
                if self.args.perf:
                    self.perf()
                self.shutdown()
        except Exception as e:  # noqa: BLE001
            rc = 2
            self.info["error"] = f"{type(e).__name__}: {e}"
            self.info["traceback"] = traceback.format_exc()[-3000:]
            c.log(f"[{self.label}] ERROR {self.info['error']}")
            if self.server is not None and not self.server.alive() and not self.server.stopped_expected:
                self.info["crash"] = True
                self.info["exitCode"] = self.server.proc.returncode if self.server.proc else None
        finally:
            if self.server is not None:
                if self.server.alive():
                    self.server.kill()
                self.check_crash()
            self.info["totalSeconds"] = round(time.time() - t_start, 1)
            if self.info["crash"]:
                rc = rc or 3
            self.run_dir.mkdir(parents=True, exist_ok=True)
            (self.run_dir / "run.json").write_text(json.dumps(self.info, indent=2), encoding="utf-8")
        return rc


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("id")
    ap.add_argument("--label", required=True, choices=["base1", "base2", "mcfc", "perfbase"])
    ap.add_argument("--run-dir")
    ap.add_argument("--zip", action="append", help="use this zip instead of manifest packs (repeatable)")
    ap.add_argument("--kind", choices=["function", "worldgen", "mixed"])
    ap.add_argument("--seed", default="20261026")
    ap.add_argument("--ticks", type=int, default=200)
    ap.add_argument("--perf", action="store_true")
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--port", type=int)
    ap.add_argument("--rcon-port", type=int)
    ap.add_argument("--xmx", default="3G")
    ap.add_argument("--boxes-file")
    ap.add_argument("--max-functions", type=int, default=0)
    ap.add_argument("--no-exercise", action="store_true")
    ap.add_argument("--cmd-timeout", type=float, default=60.0, help="RCON timeout per exerciser command")
    ap.add_argument("--exercise-budget", type=float, default=300.0, help="total seconds for the exerciser")
    ap.add_argument("--mcfc-timeout", type=float, default=600.0)
    args = ap.parse_args()
    c.ensure_dirs()
    return Runner(args).run()


if __name__ == "__main__":
    c.main_guard(main)
