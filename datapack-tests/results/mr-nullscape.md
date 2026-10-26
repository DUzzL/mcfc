# mr-nullscape: DIFF

- kind: worldgen, packs: mr-nullscape
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 9/9 functions compiled (100.0%), partial fallback commands: 17
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 1, flaky 15, low-confidence 7, noise paths 310; worldgen (gen dump) findings 11
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.1 ms (min 0.92, max 1.97); mcfc median 1.43 ms (min 1.43, max 2.32)
- mcfc debug: 8 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 8 of 9 candidate functions (macro skipped 1, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: place: 12
- Unknown command: forceload: 2
- Macros are not supported in this location: 1
- Too many arguments for @ fill <from:block_pos> <to:block_pos> <block:block_predicate> replace <filter:block_predicate>: 1
- Failed to parse any options @ execute: 1

## confirmed findings (first 20)
- [gen] `blocks[overworld].columns.-7,-5`: base="b708fecd507f0bd1bf17d8e0029c93f1cd30c798e927c09efbfe77bb45f6dbce" | mcfc="db159ee436b87721b1a3dd8d21cd5c70093b4af6995018c66a74ba65b08ac6aa"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-2,-4`
- [final] `blocks[overworld].columns.-3,-4`
- [final] `blocks[overworld].columns.0,4`
- [final] `blocks[overworld].columns.4,1`
- [final] `blocks[overworld].columns.5,2`
- [gen] `blocks[overworld].columns.-2,-4`
- [gen] `blocks[overworld].columns.-3,-4`
- [gen] `blocks[overworld].columns.-6,3`
- [gen] `blocks[overworld].columns.-6,4`
- [gen] `blocks[overworld].columns.0,4`

## low-confidence findings (7, first 10; not counted)
- `blocks[overworld].columns.-6,3`
- `blocks[overworld].columns.-6,4`
- `blocks[overworld].columns.-7,-5`
- `blocks[overworld].columns.0,5`
- `blocks[overworld].columns.5,-4`
- `blocks[overworld].columns.6,-3`
- `blocks[overworld].columns.6,-4`
