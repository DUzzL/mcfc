# guns-terralith: DIFF

- kind: worldgen, packs: mr-timber-strike, mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 53/54 functions compiled (98.2%), partial fallback commands: 84
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 33, flaky 6, low-confidence 5, noise paths 736; worldgen (gen dump) findings 6
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 2.13 ms (min 1.98, max 2.74); mcfc median 2.04 ms (min 1.85, max 2.93)
- mcfc debug: 44 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.996
- exerciser (mcfc): ran 44 of 54 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 67
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1
- Unknown command: spreadplayers: 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].customName`: base=<missing> | mcfc=null
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].nbt`: base=<missing> | mcfc="{FlippedRotation:0b,HasTicked:0b,Invulnerable:0b,LootTable:\"minecraft:chests/abandoned_mineshaft\",LootTableSeed:-5164577931938602985L}"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].pos[0]`: base=<missing> | mcfc=-70.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].pos[1]`: base=<missing> | mcfc=38.06
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].pos[2]`: base=<missing> | mcfc=20.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].tags[]`: base=<missing> | mcfc=[]
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].type`: base=<missing> | mcfc="minecraft:chest_minecart"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#10].uuid`: base=<missing> | mcfc="<chest_minecart#10>"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].customName`: base=<missing> | mcfc=null
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].nbt`: base=<missing> | mcfc="{FlippedRotation:0b,HasTicked:1b,Invulnerable:0b,LootTable:\"minecraft:chests/abandoned_mineshaft\",LootTableSeed:-8916152141466915146L}"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[0]`: base=<missing> | mcfc=-664679.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[1]`: base=<missing> | mcfc=5.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[2]`: base=<missing> | mcfc=-26096.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].tags[]`: base=<missing> | mcfc=[]
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].type`: base=<missing> | mcfc="minecraft:chest_minecart"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].uuid`: base=<missing> | mcfc="<chest_minecart#7>"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].customName`: base=<missing> | mcfc=null
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].nbt`: base=<missing> | mcfc="{FlippedRotation:0b,HasTicked:0b,Invulnerable:0b,LootTable:\"minecraft:chests/abandoned_mineshaft\",LootTableSeed:-2596915838807894336L}"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].pos[0]`: base=<missing> | mcfc=-664684.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].pos[1]`: base=<missing> | mcfc=3.06

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.6,5`
- [gen] `blocks[overworld].columns.-5,6`
- [gen] `blocks[overworld].columns.-7,3`
- [gen] `blocks[overworld].columns.-7,4`
- [gen] `blocks[overworld].columns.-8,-2`
- [gen] `blocks[overworld].columns.6,5`

## low-confidence findings (5, first 10; not counted)
- `blocks[overworld].columns.-5,6`
- `blocks[overworld].columns.-7,3`
- `blocks[overworld].columns.-7,4`
- `blocks[overworld].columns.-8,-2`
- `blocks[overworld].columns.-8,-4`
