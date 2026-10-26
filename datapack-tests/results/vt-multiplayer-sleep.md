# vt-multiplayer-sleep: OK

- kind: function, packs: vt-multiplayer-sleep
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 33/33 functions compiled (100.0%), partial fallback commands: 17
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 3; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=2
- MSPT (solo, sprint 1200 x3): vanilla median 0.29 ms (min 0.26, max 0.36); mcfc median 0.26 ms (min 0.23, max 0.33)
- mcfc debug: 28 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 26 of 33 candidate functions (macro skipped 0, name skipped 7, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: bossbar: 15
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2

## exceptions mcfc
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester1 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
