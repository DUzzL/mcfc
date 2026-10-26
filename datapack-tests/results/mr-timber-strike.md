# mr-timber-strike: FALLBACK-ISSUE

- kind: function, packs: mr-timber-strike
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 50/51 functions compiled (98.0%), partial fallback commands: 83
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 4, noise paths 98; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.28 ms (min 0.28, max 0.38); mcfc median 0.3 ms (min 0.25, max 0.41)
- mcfc debug: 41 compiled | 7 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.7739
- exerciser (mcfc): ran 41 of 51 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 67
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1

## low-confidence findings (4, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#14].pos[2]`
- `entities.minecraft:overworld.list[minecraft:item#15].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#4].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#8].pos[0]`
