# mr-incendium: DIFF

- kind: worldgen, packs: mr-incendium
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 2312/2314 functions compiled (99.9%), partial fallback commands: 255
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 2, flaky 26, low-confidence 13, noise paths 1155; worldgen (gen dump) findings 17
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.85 ms (min 0.72, max 1.07); mcfc median 0.83 ms (min 0.76, max 1.0)
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
- [gen] `blocks[overworld].columns.-3,6`: base="2367942b2b465622bd2146e4739fadd8ea717c3225049c2a19553f63a8d2a2d9" | mcfc="7674965fb8873d2b1fc71fed9d77270d5c9be602a1f779d905c9a6adc5b3d1a2"
- [gen] `blocks[overworld].columns.-5,-4`: base="0c4a0e3d2bbb7e835ac55180e1d2ffebb8d9082e127ac7b66e5f926ba2f70c87" | mcfc="828e18dd05400f93debb665a398bbe8c26c8f529c5be3d6ffa00abc90b4018b4"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[nether].columns.3,-1`
- [final] `blocks[nether].columns.3,2`
- [final] `blocks[overworld].columns.-7,1`
- [final] `blocks[overworld].columns.2,6`
- [final] `blocks[overworld].columns.6,4`
- [final] `blocks[overworld].columns.7,-2`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#3].tags[12]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#3].tags[1]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#8].tags[11]`
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#8].tags[12]`

## low-confidence findings (13, first 10; not counted)
- `blocks[overworld].columns.-2,7`
- `blocks[overworld].columns.-3,6`
- `blocks[overworld].columns.-5,-2`
- `blocks[overworld].columns.-5,-3`
- `blocks[overworld].columns.-5,-4`
- `blocks[overworld].columns.-7,0`
- `blocks[overworld].columns.2,5`
- `blocks[overworld].columns.4,-7`
- `blocks[overworld].columns.4,-8`
- `blocks[overworld].columns.5,-3`
