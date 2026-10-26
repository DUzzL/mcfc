# mr-incendium: DIFF

- kind: worldgen, packs: mr-incendium
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 2314/2314 functions compiled (100.0%), partial fallback commands: 255
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 1, flaky 42, low-confidence 4, noise paths 1674; worldgen (gen dump) findings 6
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.17 ms (min 1.11, max 2.08); mcfc median 1.35 ms (min 1.24, max 1.58)
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
- [final] `entities.minecraft:overworld.list[minecraft:piglin_brute#9].tags[10]`: base=<missing> | mcfc="in.netherite"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-2,-4`
- [final] `blocks[overworld].columns.-8,5`
- [final] `entities.minecraft:overworld.list[minecraft:bee#13].tags[3]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].customName`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].tags[0]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].tags[1]`

## low-confidence findings (4, first 10; not counted)
- `blocks[overworld].columns.-2,-3`
- `blocks[overworld].columns.-3,0`
- `blocks[overworld].columns.-4,1`
- `blocks[overworld].columns.-8,6`
