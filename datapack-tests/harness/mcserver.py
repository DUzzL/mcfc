"""Dedicated server process wrapper with a minimal RCON client."""
from __future__ import annotations

import os
import re
import secrets
import shutil
import socket
import struct
import subprocess
import time
from pathlib import Path
from typing import Iterator

import common as c


class RconError(Exception):
    pass


class Rcon:
    """Minimal Source RCON client. Handles multi-packet replies and reconnects."""

    AUTH, EXEC, RESP_VALUE = 3, 2, 0

    def __init__(self, host: str, port: int, password: str, timeout: float = 60.0):
        self.host, self.port, self.password, self.timeout = host, port, password, timeout
        self.sock: socket.socket | None = None
        self._next_id = 100
        self.reconnects = 0
        self._last_used = 0.0
        self.last_error = ""

    # -- low level -------------------------------------------------------
    def connect(self, attempts: int = 1) -> None:
        self.close()
        last: Exception | None = None
        for _ in range(attempts):
            try:
                s = socket.create_connection((self.host, self.port), timeout=10)
                s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                s.settimeout(self.timeout)
                self.sock = s
                self._last_used = time.time()
                rid = self._new_id()
                self._send(rid, self.AUTH, self.password)
                while True:
                    pid, ptype, _ = self._recv()
                    if ptype == 2:  # auth response
                        if pid == -1:
                            raise RconError("rcon auth failed")
                        return
            except (OSError, RconError) as e:
                last = e
                self.close()
                if attempts > 1:
                    time.sleep(1.0)
        raise RconError(f"rcon connect failed: {last}")

    def close(self) -> None:
        if self.sock is not None:
            try:
                self.sock.close()
            except OSError:
                pass
            self.sock = None

    def _new_id(self) -> int:
        self._next_id += 1
        if self._next_id > 2_000_000_000:
            self._next_id = 100
        return self._next_id

    def _send(self, rid: int, ptype: int, body: str) -> None:
        data = body.encode("utf-8") + b"\x00\x00"
        pkt = struct.pack("<iii", len(data) + 8, rid, ptype) + data
        assert self.sock is not None
        self.sock.sendall(pkt)

    def _recv_exact(self, n: int) -> bytes:
        assert self.sock is not None
        buf = bytearray()
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("rcon connection closed")
            buf += chunk
        return bytes(buf)

    def _recv(self) -> tuple[int, int, str]:
        (length,) = struct.unpack("<i", self._recv_exact(4))
        payload = self._recv_exact(length)
        rid, ptype = struct.unpack("<ii", payload[:8])
        body = payload[8:-2].decode("utf-8", errors="replace")
        return rid, ptype, body

    # -- public ----------------------------------------------------------
    IDLE_RECONNECT = 5.0  # reconnect proactively after a pause instead of trusting a stale socket

    def command(self, cmd: str, retry: bool = True, timeout: float | None = None) -> str:
        """Run a command and return the full reply (multi-packet aware).

        Connection errors are retried (up to 2 times) on a fresh connection; read timeouts are not
        retried (the command may still be running on a busy server).
        """
        for attempt in (0, 1, 2):
            try:
                if self.sock is None or time.time() - self._last_used > self.IDLE_RECONNECT:
                    self.connect(attempts=5)
                self.sock.settimeout(timeout or self.timeout)
                rid = self._new_id()
                end_id = self._new_id()
                self._send(rid, self.EXEC, cmd)
                parts: list[str] = []
                # The server reads one socket read per packet and drops the connection when two
                # packets arrive together, so the sentinel is only sent after the first reply packet.
                while True:
                    pid, _, body = self._recv()
                    if pid == rid:
                        parts.append(body)
                        break
                # Sentinel: an invalid packet type is answered after the remaining reply chunks.
                self._send(end_id, 100, "")
                while True:
                    pid, _, body = self._recv()
                    if pid == end_id:
                        break
                    if pid == rid:
                        parts.append(body)
                self._last_used = time.time()
                return "".join(parts)
            except socket.timeout as e:
                self.close()
                raise RconError(f"rcon timeout ({cmd[:60]!r})") from e
            except (OSError, ConnectionError, struct.error) as e:
                self.close()
                self.reconnects += 1
                self.last_error = f"{type(e).__name__}: {e} on {cmd[:40]!r}"
                if attempt == 2 or not retry:
                    raise RconError(f"rcon command failed ({cmd[:60]!r}): {e}") from e
        raise RconError("unreachable")


class Server:
    def __init__(self, run_dir: Path, variant: str, port: int, rcon_port: int,
                 xmx: str = "3G", seed: str = "20261026", level_type: str = "minecraft:normal"):
        self.run_dir = Path(run_dir)
        self.variant = variant
        self.port = port
        self.rcon_port = rcon_port
        self.xmx = xmx
        self.seed = seed
        self.level_type = level_type
        self.password = secrets.token_hex(12)
        self.proc: subprocess.Popen | None = None
        self.rcon: Rcon | None = None
        self.console_path = self.run_dir / "console.log"
        self._console_fh = None
        self.started_at = 0.0
        self.stopped_expected = False

    # -- paths -----------------------------------------------------------
    @property
    def latest_log(self) -> Path:
        return self.run_dir / "logs" / "latest.log"

    @property
    def world_dir(self) -> Path:
        return self.run_dir / "world"

    # -- lifecycle -------------------------------------------------------
    def prepare(self, mods: list[Path], extra_props: dict[str, str] | None = None) -> None:
        if self.run_dir.exists():
            shutil.rmtree(self.run_dir)
        shutil.copytree(c.CACHE_TEMPLATE, self.run_dir)
        mods_dir = self.run_dir / "mods"
        mods_dir.mkdir(exist_ok=True)
        for m in mods:
            shutil.copy2(m, mods_dir / m.name)
        (self.run_dir / "eula.txt").write_text("eula=true\n", encoding="ascii")
        props = {
            "server-ip": "127.0.0.1",
            "server-port": str(self.port),
            "online-mode": "false",
            "enable-rcon": "true",
            "rcon.port": str(self.rcon_port),
            "rcon.password": self.password,
            "broadcast-rcon-to-ops": "false",
            "max-tick-time": "-1",
            "spawn-protection": "0",
            "view-distance": "6",
            "simulation-distance": "6",
            "sync-chunk-writes": "true",
            "level-seed": self.seed,
            "level-type": self.level_type,
            "generate-structures": "true",
            "motd": f"mcfc-test {self.variant}",
            "enable-query": "false",
            "enable-status": "false",
            "difficulty": "normal",
            "spawn-monsters": "false",
            "spawn-animals": "false",
            "spawn-npcs": "false",
            "allow-flight": "true",
            "enforce-secure-profile": "false",
        }
        props.update(extra_props or {})
        text = "".join(f"{k}={v}\n" for k, v in props.items())
        (self.run_dir / "server.properties").write_text(text, encoding="utf-8")

    def start(self) -> None:
        self._console_fh = open(self.console_path, "ab")
        cmd = ["java", f"-Xmx{self.xmx}", "-Xms512M", "-jar", "fabric-server-launch.jar", "nogui"]
        flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        self.proc = subprocess.Popen(cmd, cwd=self.run_dir, stdin=subprocess.PIPE,
                                     stdout=self._console_fh, stderr=subprocess.STDOUT,
                                     creationflags=flags)
        self.started_at = time.time()
        self.rcon = Rcon("127.0.0.1", self.rcon_port, self.password)

    def early_freeze(self, timeout: float = 900.0) -> str:
        """Connect to RCON as soon as the port opens and send `tick freeze`.

        The command is queued on the server thread before its first tick, so (almost) no tick
        runs before the freeze. Returns the command reply.
        """
        assert self.rcon is not None
        t0 = time.time()
        while time.time() - t0 < timeout:
            if not self.alive():
                raise RuntimeError("server exited during startup")
            try:
                self.rcon.connect(attempts=1)
            except RconError:
                time.sleep(0.02)
                continue
            return self.rcon.command("tick freeze", retry=False, timeout=300)
        raise TimeoutError("rcon never became available")

    def alive(self) -> bool:
        return self.proc is not None and self.proc.poll() is None

    def wait_ready(self, timeout: float = 900.0) -> float:
        """Poll logs/latest.log for the 'Done (' line. Returns seconds taken."""
        t0 = time.time()
        while time.time() - t0 < timeout:
            if not self.alive():
                raise RuntimeError(f"server exited during startup (code {self.proc.returncode})")
            if self.latest_log.exists() and self.grep_log(r"\]: Done \(", max_hits=1):
                break
            time.sleep(1.0)
        else:
            raise TimeoutError("server did not become ready")
        assert self.rcon is not None
        self.rcon.connect(attempts=30)
        return time.time() - t0

    def stop(self, timeout: float = 180.0) -> int | None:
        """Flush, stop and wait; kill as a fallback. Returns the exit code."""
        self.stopped_expected = True
        if self.proc is None:
            return None
        if self.alive():
            try:
                assert self.rcon is not None
                self.rcon.command("save-all flush", retry=False)
            except Exception:
                pass
            try:
                self.rcon.command("stop", retry=False)
            except Exception:
                pass
            try:
                self.proc.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=30)
        if self.rcon:
            self.rcon.close()
        if self._console_fh:
            self._console_fh.close()
            self._console_fh = None
        return self.proc.returncode

    def kill(self) -> None:
        if self.proc is not None and self.alive():
            self.proc.kill()
            try:
                self.proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                pass
        if self.rcon:
            self.rcon.close()
        if self._console_fh:
            self._console_fh.close()
            self._console_fh = None

    # -- rcon helpers ----------------------------------------------------
    def cmd(self, command: str, timeout: float | None = None) -> str:
        assert self.rcon is not None
        if not self.alive():
            raise RuntimeError("server is not running")
        return self.rcon.command(command, timeout=timeout)

    # -- log helpers -----------------------------------------------------
    def log_size(self) -> int:
        try:
            return self.latest_log.stat().st_size
        except OSError:
            return 0

    def iter_log(self, since: int = 0) -> Iterator[str]:
        p = self.latest_log
        if not p.exists():
            return
        with open(p, "rb") as f:
            f.seek(since)
            for raw in f:
                yield raw.decode("utf-8", errors="replace").rstrip("\r\n")

    def grep_log(self, pattern: str, since: int = 0, max_hits: int = 100000) -> list[str]:
        rx = re.compile(pattern)
        out: list[str] = []
        for line in self.iter_log(since):
            if rx.search(line):
                out.append(line)
                if len(out) >= max_hits:
                    break
        return out

    def wait_log(self, pattern: str, since: int = 0, timeout: float = 120.0) -> str:
        rx = re.compile(pattern)
        t0 = time.time()
        pos = since
        while time.time() - t0 < timeout:
            for line in self.iter_log(pos):
                if rx.search(line):
                    return line
            if not self.alive():
                raise RuntimeError("server died while waiting for log pattern " + pattern)
            time.sleep(0.5)
        raise TimeoutError(f"log pattern not seen: {pattern}")

    # -- game helpers ----------------------------------------------------
    def gametime(self, timeout: float | None = 600.0) -> int:
        reply = self.cmd("time query gametime", timeout=timeout)
        m = re.search(r"(-?\d+)", reply)
        if not m:
            raise RuntimeError(f"cannot parse gametime from {reply!r}")
        return int(m.group(1))

    def tick_step(self, n: int, timeout: float = 600.0) -> float:
        """Step n ticks while frozen and wait until they have run. Returns seconds."""
        t0 = time.time()
        before = self.gametime()
        reply = self.cmd(f"tick step {n}")
        if "tick" not in reply.lower() and "step" not in reply.lower():
            raise RuntimeError(f"unexpected tick step reply: {reply!r}")
        while time.time() - t0 < timeout:
            if self.gametime() - before >= n:
                # make sure the stepping state finished (tick query shows frozen without stepping)
                return time.time() - t0
            time.sleep(0.05)
        raise TimeoutError(f"tick step {n} did not finish")


    def mcfc_state(self) -> str:
        return self.cmd("mcfc state").strip()

    SETTLED_STATES = ("READY_ON", "READY_OFF", "FAILED", "EXECUTION_FAILED")

    def wait_mcfc_ready(self, timeout: float = 600.0) -> str:
        """Poll `/mcfc state` until READY_ON. Returns the state text.

        Other settled states (FAILED, READY_OFF, ...) are returned immediately so the caller can
        record them; a timeout raises TimeoutError.
        """
        t0 = time.time()
        last = ""
        while time.time() - t0 < timeout:
            last = self.mcfc_state()
            m = self.MCFC_STATE_RX.search(last)
            name = m.group(1) if m else ""
            if name in self.SETTLED_STATES:
                return last
            if name == "NOTHING_LOADED" and "Total Function Count: 0" in last and time.time() - t0 > 30:
                return last
            if not self.alive():
                raise RuntimeError("server died while waiting for mcfc")
            time.sleep(2.0)
        raise TimeoutError(f"mcfc did not settle, last state: {last!r}")


    MCFC_STATE_RX = re.compile(r"State:\s*([A-Z_]+?)(?=Compiled|\s|$)")

    def mcfc_state_name(self) -> str:
        m = self.MCFC_STATE_RX.search(self.mcfc_state())
        return m.group(1) if m else "UNKNOWN"

    def enabled_packs(self) -> list[str]:
        reply = self.cmd("datapack list enabled")
        return re.findall(r"file/[^]\", ]+", reply)

    def reload_and_wait(self, expect_pack: str | None = None, timeout: float = 300.0) -> bool:
        """Run /reload and wait until it has been applied.

        The enabled pack list only changes once the reload is applied on the server
        thread, so a newly added pack (expect_pack, e.g. 'file/x.zip') is the barrier.
        The advancement-loading log line marks the end of the background phase. Returns
        True when expect_pack is enabled afterwards (always True without expect_pack).
        """
        pos = self.log_size()
        self.cmd("reload")
        self.wait_log(r"Loaded \d+ advancements", since=pos, timeout=timeout)
        if not expect_pack:
            time.sleep(2.0)
            return True
        t0 = time.time()
        while time.time() - t0 < 30.0:
            if expect_pack in self.enabled_packs():
                return True
            time.sleep(0.5)
        return False

    def sprint(self, ticks: int, timeout: float = 1800.0) -> tuple[float, float, str]:
        """Run /tick sprint and parse the completion line. Returns (tps, ms_per_tick, line)."""
        pos = self.log_size()
        reply = self.cmd(f"tick sprint {ticks}")
        line = self.wait_log(r"Sprint completed with", since=pos, timeout=timeout)
        m = re.search(r"with ([\d.,]+) ticks per second, or ([\d.,]+) ms per tick", line)
        if not m:
            raise RuntimeError(f"cannot parse sprint line: {line!r} (reply {reply!r})")
        return float(m.group(1).replace(",", ".")), float(m.group(2).replace(",", ".")), line
