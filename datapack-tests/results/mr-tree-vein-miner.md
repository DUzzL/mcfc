# mr-tree-vein-miner: OK

- kind: function, packs: mr-tree-vein-miner
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 70/70 functions compiled (100.0%), partial fallback commands: 6
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 4, noise paths 174; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.39 ms (min 0.31, max 0.47); mcfc median 0.35 ms (min 0.3, max 0.4)
- mcfc debug: 27 compiled | 5 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.3582
- exerciser (mcfc): ran 53 of 70 candidate functions (macro skipped 12, name skipped 5, scenario skipped 0), errors 32, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 6

## low-confidence findings (4, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#27].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#32].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#5].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#9].pos[1]`
