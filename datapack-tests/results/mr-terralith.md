# mr-terralith: DIFF

- kind: worldgen, packs: mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 3/3 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 10, flaky 16, low-confidence 173, noise paths 2880; worldgen (gen dump) findings 7
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.36 ms (min 1.35, max 1.59); mcfc median 1.1 ms (min 1.02, max 1.52)
- mcfc debug: 3 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 3 of 3 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: spreadplayers: 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].customName`: base=<missing> | mcfc=null
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].nbt`: base=<missing> | mcfc="{FlippedRotation:0b,HasTicked:1b,Invulnerable:0b,LootTable:\"minecraft:chests/abandoned_mineshaft\",LootTableSeed:1498905817692126562L}"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[0]`: base=<missing> | mcfc=264819.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[1]`: base=<missing> | mcfc=-7.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].pos[2]`: base=<missing> | mcfc=942334.5
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].tags[]`: base=<missing> | mcfc=[]
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].type`: base=<missing> | mcfc="minecraft:chest_minecart"
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#7].uuid`: base=<missing> | mcfc="<chest_minecart#7>"
- [gen] `blocks[overworld].columns.-8,-4`: base="13d848608321fbde176cbaf567b328519fd11c3f596d2097d3ed1e85f2d6aee7" | mcfc="b15e20ba06e454b600a8483cfb20fe22dfe43e54525d29c01c9cee928cfe7f13"
- [gen] `blocks[overworld].columns.2,3`: base="7ba72e60248df906289fde9e7c8fce552815be3ad68c3c624fd822f76115b924" | mcfc="95c771988c48fb7862ed67eb949eab065a97accb3a7575b17ac90ea12a21d3ab"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.3,-5`
- [final] `blocks[overworld].columns.6,-1`
- [final] `blocks[overworld].columns.6,-2`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].customName`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:chest_minecart#8].type`

## low-confidence findings (173, first 10; not counted)
- `blocks[overworld].columns.-4,-2`
- `blocks[overworld].columns.-5,-2`
- `blocks[overworld].columns.-8,-4`
- `blocks[overworld].columns.-8,1`
- `blocks[overworld].columns.2,3`
- `entities.minecraft:overworld.list[minecraft:item#51].customName`
- `entities.minecraft:overworld.list[minecraft:item#51].nbt`
- `entities.minecraft:overworld.list[minecraft:item#51].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#51].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#51].pos[2]`
