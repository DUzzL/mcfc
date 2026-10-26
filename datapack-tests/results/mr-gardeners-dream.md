# mr-gardeners-dream: OK

- kind: function, packs: mr-gardeners-dream
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 54/54 functions compiled (100.0%), partial fallback commands: 23
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 39; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=2, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.23 ms (min 0.19, max 0.3); mcfc median 0.24 ms (min 0.19, max 0.31)
- mcfc debug: 36 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 53 of 54 candidate functions (macro skipped 0, name skipped 0, scenario skipped 1), errors 32, slow 0

## top partial-fallback reasons
- Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate>: 20
- Unknown command: msg: 2
- Expected a selector: 1

## exceptions base2
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/3c60c0c23d05399fbffb864ec993da33?unsigned
- java.net.SocketTimeoutException x1: Read timed out
