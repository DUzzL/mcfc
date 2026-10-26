# mr-incendium-tp: DIFF

- kind: mixed, packs: mr-incendium
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 2312/2314 functions compiled (99.9%), partial fallback commands: 255
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 11, flaky 16, low-confidence 9, noise paths 1671; worldgen (gen dump) findings 11
- exceptions (non-ignorable lines): base1=0, base2=2, mcfc=0
- mcfc debug: 1933 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 2295 of 2314 candidate functions (macro skipped 4, name skipped 15, scenario skipped 0), errors 380, slow 0

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
- [final] `entities.minecraft:overworld.list[minecraft:bee#2].pos[0]`: base=-77.5 | mcfc=-114.55
- [final] `entities.minecraft:overworld.list[minecraft:bee#2].pos[1]`: base=65.25 | mcfc=64.72
- [final] `entities.minecraft:overworld.list[minecraft:bee#2].pos[2]`: base=-137.67 | mcfc=-111.95
- [final] `entities.minecraft:overworld.list[minecraft:bee#2].tags[1]`: base="in.eid_0" | mcfc="in.eid_1"
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].tags[2]`: base="in.eid_4" | mcfc=<missing>
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].tags[3]`: base="in.ghast_sentry" | mcfc=<missing>
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#2].customName`: base="{\"fallback\":\"Piglin Knight\",\"translate\":\"incendium.mob.castle.knight.name\"}" | mcfc="{\"fallback\":\"Piglin Pyromancer\",\"translate\":\"incendium.mob.castle.pyro.name\"}"
- [gen] `blocks[overworld].columns.-3,-3`: base="72e37e750e865bf5d8208675409ec52f506900e41e5bb188b2ea0e5976c6ad6a" | mcfc="b6aea34123d6321c95987d60d51f65b4c27b525a8c09a4d002b6e64c7242320c"
- [gen] `blocks[overworld].columns.-5,-3`: base="d05e2ecfc9dcded3a14abd646ad3541d2a9dee6b28c0aa2d888944cd3fbc785e" | mcfc="73aca44320dfcff20e4647b6e02ed951bef5115009bfea55f257debb0e6a0041"
- [gen] `blocks[overworld].columns.-5,-4`: base="0c4a0e3d2bbb7e835ac55180e1d2ffebb8d9082e127ac7b66e5f926ba2f70c87" | mcfc="92cd0faaa73cca1611ae852c28315145c4bb6b3393f99beea3c737ebd1fbf5b5"
- [gen] `blocks[overworld].columns.-7,-5`: base="b708fecd507f0bd1bf17d8e0029c93f1cd30c798e927c09efbfe77bb45f6dbce" | mcfc="db159ee436b87721b1a3dd8d21cd5c70093b4af6995018c66a74ba65b08ac6aa"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.3,6`
- [final] `blocks[overworld].columns.3,7`
- [final] `entities.minecraft:overworld.list[minecraft:bee#3].tags[4]`
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].customName`
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].tags[0]`
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].type`
- [final] `entities.minecraft:the_nether.list[minecraft:ghast#1].uuid`
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#4].tags[10]`
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#7].tags[1]`
- [gen] `blocks[nether].columns.0,0`

## low-confidence findings (9, first 10; not counted)
- `blocks[nether].columns.0,0`
- `blocks[overworld].columns.-3,-3`
- `blocks[overworld].columns.-4,5`
- `blocks[overworld].columns.-5,-3`
- `blocks[overworld].columns.-5,-4`
- `blocks[overworld].columns.-7,-5`
- `blocks[overworld].columns.7,-4`
- `blocks[overworld].columns.7,-7`
- `entities.minecraft:overworld.list[minecraft:item#10].nbt`

## exceptions base2
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/3c60c0c23d05399fbffb864ec993da33?unsigned
- java.net.SocketTimeoutException x1: Read timed out
