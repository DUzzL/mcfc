# Differential datapack tests for mcfc

Runs a Minecraft 26.3 Fabric dedicated server with one or more datapacks without mcfc (`base1`, `base2`) and with
the mcfc jar (`mcfc`), dumps the world state after a fixed number of ticks and compares the dumps. Python 3.13,
standard library only.

## Layout

- `harness/` code (this repo). `manifest.json`, `scenarios/<id>.json` and `results/` live next to it.
- Work directory `WORK` = env `MCFC_WORK` or `C:\Users\duzzl\Documents\MCFC-work`:
  `cache\server` (launcher, vanilla server jar), `cache\mods` (`carpet.jar`, `dpdump.jar`),
  `cache\packs\<id>.zip`, `cache\template` (booted server dir copied for every run),
  `runs\<id>\{base1,base2,mcfc,perfbase}` and `runs\<id>\attempt2\...` (confirmation attempt).

## How to run

```
python harness\fetch_runtime.py          # launcher, server jar, Carpet, template dir (sha verified)
python harness\fetch_packs.py            # fills cache\packs and manifest.json
python harness\run_pack.py <id> [--no-perf] [--no-confirm] [--ticks 200] [--reps 3] [--max-functions N]
                                [--exercise-budget 300] [--cmd-timeout 60]
python harness\run_pack.py _counter --zip C:\...\cache\packs\_counter.zip   # ad-hoc zip
python harness\packs.py                  # writes cache\packs\_counter.zip (tick counter test pack)
```

`<id>` is a pack id or a combination id from `manifest.json`. Order of servers (never more than 2, perf never
measured while another server runs):

1. `base1` + `base2` concurrently (no perf)
2. `mcfc` alone, with perf
3. `perfbase` alone: a vanilla run with the same steps, only used for the perf number and as an extra noise
   baseline (skipped with `--no-perf`, which also runs step 2 without perf)
4. if there are findings and `--confirm` is on (default): steps 1 and 2 again under `runs\<id>\attempt2`

Then `compare` and `logscan`. Report: `results\<id>.md` and `.json`; raw data (console.log, logs, dumps, run.json,
logscan.json, compare-final.json, compare-gen.json) stays in `WORK\runs\<id>\`. Single steps: `run_variant.py <id>
--label base1|base2|mcfc|perfbase`, `compare.py <runs\id> --dump final|gen [--gen-only] [--lowconf]`,
`logscan.py <run dir>`.

Status: `OK`, `DIFF` (confirmed findings), `CRASH` (mcfc run failed or crashed), `FALLBACK-ISSUE` (no diff, but
compile failure, mcfc state other than READY_ON, whole-function fallback, runtime disable or compiler error),
`INCOMPLETE` (a baseline run failed). The report also shows the mcfc final state and, when it is not READY_ON, the
first decisive mcfc error (log line plus exception class and message).

## What one run does

1. Copy the template, add mods (Carpet, dpdump, mcfc for the mcfc variant), write `server.properties`
   (offline mode, fixed seed 20261026, view/simulation distance 6, RCON on a random password, `sync-chunk-writes`).
   Kind `function`: flat world with `generate-structures=false`, packs are copied after start and loaded with
   `/reload`. Kinds `worldgen`/`mixed`: normal world, packs are in `world/datapacks` before the first start. Packs
   with an outdated pack format (manifest `needsPackFormatPatch` or detected) get `pack.mcmeta` rewritten (format
   fields only; all other zip entries are copied unchanged); the change is logged in the notes.
2. `tick freeze` (sent over RCON as soon as the port opens, before the first tick; 1-5 ticks still run before it),
   game rules (names are looked up, see below), force-load of the dump boxes, throwaway dump `warm` (generates
   chunks). Worldgen kinds: dump `gen` right after it (still untouched). Then `NoAI` on all existing non-player
   entities.
3. Carpet fake players `DPTester1`/`DPTester2` at x=0/x=3 on the surface.
4. Load packs, verify `datapack list enabled`, mcfc: `mcfc debug on` and wait for a settled state (READY_ON, FAILED,
   ...), `function #minecraft:load`, `tick step 1` (the load tag of the reload is queued for the next tick while
   frozen).
5. Optional `scenarios/<id>.json`: either a list of steps (`{"cmd"}`, `{"step"}`, `{"dump"}`) or
   `{"steps": [...], "exerciserSkip": ["regex", ...]}`.
6. Generic exerciser: dump `pre`; every function in the pack namespaces (from the dumper function list) except
   macro functions, names matching `uninstall|disable|reset|clear|remove_all|delete|kill_all|wipe` and the
   scenario's `exerciserSkip` regexes (static rules, so every variant gets the same list), sorted, as
   `execute as DPTester1 at @s run function <id>`, `tick step 1` after every 50. Limits: RCON timeout per command
   (`--cmd-timeout`, 60 s) and a total budget (`--exercise-budget`, 300 s). A function over the timeout is recorded as
   `slow` in run.json, the harness waits until the server answers again and continues. When the budget runs out the
   rest is skipped (`budgetExceeded`, `notRun`); the cut-off differs between variants, the report warns when the
   number of functions run differs.
7. `tick step <ticks>`, dump `final`; mcfc: `mcfc debug flush`, `debug off`.
8. Perf (mcfc and perfbase runs): restore `random_tick_speed`, `tick unfreeze`, warm-up `tick sprint 200`, then
   `--reps` (3) times `tick sprint 1200`; the line `Sprint completed with X ticks per second, or Y ms per tick` is
   read from `logs/latest.log`. The report gives median, min and max ms per tick.

## Comparison

`compare.py` takes the dumps of the runs. `meta` is dropped; entities are sorted by type/position/name/tags; UUIDs
(string and `[I;a,b,c,d]` forms, also inside NBT strings and keys) are renamed to labels (`<type>#<n>` per entity
type, players keep their name). Dumps are flattened to path -> value (entities keyed by `type#index`, block boxes by
name and column key).

- Noise = paths where the vanilla runs disagree. Attempt 1 uses base1, base2 and perfbase; once a second attempt
  exists, all vanilla runs of both attempts are used for both comparisons.
- Finding = path outside the noise where mcfc differs from base1.
- `gen` dump (worldgen kinds): blocks and entity counts only; findings here are the authoritative worldgen check.
- `final` dump in worldgen kinds: findings under entity types `minecraft:falling_block`, `minecraft:item`,
  `minecraft:experience_orb`, and block columns that changed between `gen` and `final` in any vanilla run are
  "low-confidence"; they are listed separately and do not count towards DIFF.
- Confirmation: findings that appear in both attempts are "confirmed" (status DIFF), the others are "flaky".

## Determinism measures

Fixed seed, offline UUIDs, early `tick freeze` and all progress through `tick step`, fixed order of exerciser
commands, `max_command_sequence_length` = 2147483647 in all variants (mcfc ignores that limit), mob spawning,
weather cycle and random ticks off, no structures in flat worlds, `NoAI` for pre-existing mobs, identical dump
boxes, no real-time dependence (gametime only advances by stepping).

## Known limitations

- 1-5 ticks still run before the early `tick freeze`; gametime differs and is classified as noise.
- Vanilla world generation of the same seed is not fully deterministic in this setup (identical vanilla runs differ
  in individual block columns, e.g. around structures). These columns are noise, but a column that is stable in all
  vanilla runs and differs in mcfc runs can still appear as a finding by chance; judge such findings by whether
  they repeat on other runs and packs.
- RCON drops the connection when two packets arrive in one read, so the client sends the end-of-reply marker only
  after the first reply packet; after a connection error a command is retried up to 2 times (a command may then run
  twice if its reply was lost).
- A tick step that takes very long (e.g. nuke functions, minutes) blocks all RCON commands; tick steps wait up to
  600 s.
- Only the boxes given in `dpdump.json` are dumped (overworld, plus nether/end for worldgen kinds).

## Additions (jar pinning, diagnostics, batch runs)

- `--mcfc-jar PATH` (run_pack, run_variant): default is the newest frozen `WORK/cache/mods/mcfc-<sha>.jar` by mtime
  (never `build/libs`). File name and sha256 go to `run.json` and the `mcfc jar` line of the report.
- After the mcfc variant stops, `generated/diagnostics.tsv` (kind, id, function, message, command) is copied to
  the run dir root and to `results/<id>.diagnostics.tsv`.
- Findings under `minecraft:item`, `minecraft:experience_orb` and `minecraft:falling_block` are low confidence for
  every pack kind and never count towards DIFF.
- `manifest.json` entries with `"skip": true` (and a `skipReason`) are not run (`--force` overrides). Combinations no
  longer contain skipped packs.
- `run_pack.py --all [--include-combos] [--filter REGEX]` runs every non-skipped pack sequentially and writes
  `results/_summary.{md,json}`; `--summary-only` rebuilds the summary from existing results.
- `diag_pass.py`: one mcfc server per pack/combination without exerciser or compare, collects
  `WORK/diag/<id>.tsv` (2 servers at a time). `unsupported_report.py` aggregates them into
  `results/_unsupported.{md,json}`.
- `perf_ab.py`: solo perf A/B of vanilla and two mcfc jars on one pack (default bacap, order A B C C B A),
  writes `results/_perf_ab.{md,json}`.
