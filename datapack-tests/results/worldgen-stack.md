# worldgen-stack: DIFF

- kind: worldgen, packs: mr-terralith, mr-incendium, mr-nullscape, mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 2327/2327 functions compiled (100.0%), partial fallback commands: 273
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 3, flaky 6, low-confidence 11, noise paths 3007; worldgen (gen dump) findings 6
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.99 ms (min 1.56, max 2.02); mcfc median 1.83 ms (min 1.69, max 2.58)
- mcfc debug: 1939 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 2307 of 2327 candidate functions (macro skipped 5, name skipped 15, scenario skipped 0), errors 392, slow 0

## top partial-fallback reasons
- Failed to parse any options @ execute: 90
- Unknown command: attribute: 56
- Unknown command: rotate: 50
- Invalid integer: 15
- Unknown command: place: 13
- Unknown command: damage: 11
- Unknown command: bossbar: 8
- Unknown command: forceload: 7
- Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word>: 7
- Macros are not supported in this location: 5
- Unknown command: spreadplayers: 4
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Mixed positive and negated type options are resolved by vanilla: 1
- Too many arguments for @ fill <from:block_pos> <to:block_pos> <block:block_predicate> replace <filter:block_predicate>: 1
- Missing space after argument @ summon <entity:resource_location>: 1

## confirmed findings (first 20)
- [final] `blocks[nether].columns.2,-2`: base="30dca3cc3b05e5bea2cba43c3ba05cf265b85a3ffe415c880b31c7773c690fbb" | mcfc="480a18eea50ecaedc5dff10021800edc1f2a96ec667f2034b61175cbcbb2d455"
- [gen] `blocks[nether].columns.2,-2`: base="30dca3cc3b05e5bea2cba43c3ba05cf265b85a3ffe415c880b31c7773c690fbb" | mcfc="480a18eea50ecaedc5dff10021800edc1f2a96ec667f2034b61175cbcbb2d455"
- [gen] `blocks[overworld].columns.-8,1`: base="64603dd2fc98b730c6fddc1a16ec73b79be8a5db780409f1c610effebc5bb035" | mcfc="2d50060c48229ebc5447e3716ca086b0de229f61e924d0269e4a32fa597fa777"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-3,-8`
- [final] `blocks[overworld].columns.4,4`
- [gen] `blocks[overworld].columns.-3,-8`
- [gen] `blocks[overworld].columns.-8,-2`
- [gen] `blocks[overworld].columns.0,2`
- [gen] `blocks[overworld].columns.4,4`

## low-confidence findings (11, first 10; not counted)
- `blocks[overworld].columns.-8,-2`
- `blocks[overworld].columns.-8,1`
- `blocks[overworld].columns.0,2`
- `entities.minecraft:overworld.list[minecraft:item#65].customName`
- `entities.minecraft:overworld.list[minecraft:item#65].tags[0]`
- `entities.minecraft:overworld.list[minecraft:item#65].type`
- `entities.minecraft:overworld.list[minecraft:item#65].uuid`
- `entities.minecraft:overworld.list[minecraft:item#66].customName`
- `entities.minecraft:overworld.list[minecraft:item#66].tags[0]`
- `entities.minecraft:overworld.list[minecraft:item#66].type`
