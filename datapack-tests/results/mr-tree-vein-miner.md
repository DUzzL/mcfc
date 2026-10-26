# mr-tree-vein-miner: FALLBACK-ISSUE

- kind: function, packs: mr-tree-vein-miner
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 68/70 functions compiled (97.1%), partial fallback commands: 6
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 10, noise paths 167; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.33 ms (min 0.29, max 0.46); mcfc median 0.35 ms (min 0.28, max 0.4)
- mcfc debug: 27 compiled | 7 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.3091
- exerciser (mcfc): ran 53 of 70 candidate functions (macro skipped 12, name skipped 5, scenario skipped 0), errors 32, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 6

## low-confidence findings (10, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#1].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#22].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#43].customName`
- `entities.minecraft:overworld.list[minecraft:item#43].nbt`
- `entities.minecraft:overworld.list[minecraft:item#43].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#43].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#43].pos[2]`
- `entities.minecraft:overworld.list[minecraft:item#43].tags[]`
- `entities.minecraft:overworld.list[minecraft:item#43].type`
- `entities.minecraft:overworld.list[minecraft:item#43].uuid`
