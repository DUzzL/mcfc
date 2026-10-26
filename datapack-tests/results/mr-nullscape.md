# mr-nullscape: FALLBACK-ISSUE

- kind: worldgen, packs: mr-nullscape
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 8/9 functions compiled (88.9%), partial fallback commands: 17
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 0, flaky 14, low-confidence 2, noise paths 324; worldgen (gen dump) findings 8
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.75 ms (min 0.67, max 1.17); mcfc median 0.61 ms (min 0.53, max 1.17)
- mcfc debug: 8 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 8 of 9 candidate functions (macro skipped 1, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: place: 12
- Unknown command: forceload: 2
- Macros are not supported in this location: 1
- Too many arguments for @ fill <from:block_pos> <to:block_pos> <block:block_predicate> replace <filter:block_predicate>: 1
- Failed to parse any options @ execute: 1

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-1,1`
- [final] `blocks[overworld].columns.-8,-8`
- [final] `blocks[overworld].columns.0,0`
- [final] `blocks[overworld].columns.0,1`
- [final] `blocks[overworld].columns.0,6`
- [final] `blocks[overworld].columns.0,7`
- [gen] `blocks[overworld].columns.-1,1`
- [gen] `blocks[overworld].columns.-8,-8`
- [gen] `blocks[overworld].columns.0,0`
- [gen] `blocks[overworld].columns.0,1`

## low-confidence findings (2, first 10; not counted)
- `blocks[overworld].columns.4,-3`
- `blocks[overworld].columns.5,-3`

## exceptions perfbase
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester1 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
