# mr-timber-strike: OK

- kind: function, packs: mr-timber-strike
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 51/51 functions compiled (100.0%), partial fallback commands: 83
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 31, noise paths 111; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.38 ms (min 0.34, max 0.45); mcfc median 0.3 ms (min 0.28, max 0.46)
- mcfc debug: 41 compiled | 6 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.7824
- exerciser (mcfc): ran 41 of 51 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 67
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1

## low-confidence findings (31, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#22].customName`
- `entities.minecraft:overworld.list[minecraft:item#22].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#22].tags[]`
- `entities.minecraft:overworld.list[minecraft:item#22].type`
- `entities.minecraft:overworld.list[minecraft:item#22].uuid`
- `entities.minecraft:overworld.list[minecraft:item#23].customName`
- `entities.minecraft:overworld.list[minecraft:item#23].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#23].tags[]`
- `entities.minecraft:overworld.list[minecraft:item#23].type`
- `entities.minecraft:overworld.list[minecraft:item#23].uuid`
