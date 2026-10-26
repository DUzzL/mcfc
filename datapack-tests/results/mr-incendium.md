# mr-incendium: DIFF

- kind: worldgen, packs: mr-incendium
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 2312/2314 functions compiled (99.9%), partial fallback commands: 255
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 6, flaky 10, low-confidence 224, noise paths 1248; worldgen (gen dump) findings 5
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.72 ms (min 0.69, max 1.03); mcfc median 0.87 ms (min 0.74, max 1.08)
- mcfc debug: 31 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 2295 of 2314 candidate functions (macro skipped 4, name skipped 15, scenario skipped 0), errors 2295, slow 0

## top partial-fallback reasons
- Failed to parse any options @ execute: 89
- Unknown command: attribute: 56
- Unknown command: rotate: 50
- Invalid integer: 15
- Unknown command: damage: 11
- Unknown command: bossbar: 8
- Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word>: 7
- Unknown command: forceload: 5
- Macros are not supported in this location: 4
- Unknown command: spreadplayers: 3
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Mixed positive and negated type options are resolved by vanilla: 1
- Missing space after argument @ summon <entity:resource_location>: 1
- Only one entity is allowed, but the provided selector allows more than one: 1
- Combined type options are resolved by vanilla: 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].tags[3]`: base="in.eid_4" | mcfc="in.eid_3"
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].tags[4]`: base=<missing> | mcfc="in.eid_4"
- [final] `entities.minecraft:overworld.list[minecraft:bee#8].tags[2]`: base="in.eid_4" | mcfc=<missing>
- [gen] `blocks[overworld].columns.-6,3`: base="6a042df4dbe53bc8f81b4dc5552de9f961c99b5c7159f46fe6a19c7d5d72ac61" | mcfc="88cd69a4a52b121583c047184d48cbff71937489e02dd81c313cc9d85b42f02e"
- [gen] `blocks[overworld].columns.-6,4`: base="b18232bb025cef3c6fe89fb2212ec1b42712cb0ea5882adcd1643f33c995d205" | mcfc="6a21cf8e2c89846572196e83dfb68227f39ae841229bce883dde272b8af958ae"
- [gen] `blocks[overworld].columns.6,-8`: base="3ec20b0a199816b614aeeb8d7b5aadff4d54acfc8f225154ff8e10b2b04f8b08" | mcfc="1dfb537e227618ffc89ec6872f018561876d6c881fcf47b905d581fa7f6dceac"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[nether].columns.0,-1`
- [final] `entities.minecraft:overworld.list[minecraft:bee#10].tags[4]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#1].tags[1]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#6].tags[12]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#6].tags[13]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#9].tags[1]`
- [final] `scoreboard.scores.DPTester1.in.eid`
- [final] `scoreboard.scores.DPTester2.in.eid`
- [gen] `blocks[nether].columns.0,-1`
- [gen] `blocks[overworld].columns.-3,4`

## low-confidence findings (224, first 10; not counted)
- `blocks[overworld].columns.-3,4`
- `blocks[overworld].columns.-6,3`
- `blocks[overworld].columns.-6,4`
- `blocks[overworld].columns.0,-2`
- `entities.minecraft:overworld.list[minecraft:falling_block#10].nbt`
- `entities.minecraft:overworld.list[minecraft:falling_block#10].pos[0]`
- `entities.minecraft:overworld.list[minecraft:falling_block#10].pos[1]`
- `entities.minecraft:overworld.list[minecraft:falling_block#10].pos[2]`
- `entities.minecraft:overworld.list[minecraft:falling_block#10].tags[1]`
- `entities.minecraft:overworld.list[minecraft:falling_block#11].nbt`
