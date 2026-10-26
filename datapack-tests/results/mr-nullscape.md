# mr-nullscape: DIFF

- kind: worldgen, packs: mr-nullscape
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 7/9 functions compiled (77.8%), partial fallback commands: 19
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 2, flaky 11, low-confidence 11, noise paths 306; worldgen (gen dump) findings 8
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.63 ms (min 0.61, max 1.13); mcfc median 0.87 ms (min 0.56, max 1.24)
- mcfc debug: 7 compiled | 0 vanilla | 1 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9953
- exerciser (mcfc): ran 8 of 9 candidate functions (macro skipped 1, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: place: 12
- Missing space after argument @ execute unless predicate <predicate:resource_location>: 2
- Unknown command: forceload: 2
- Macros are not supported in this location: 1
- Too many arguments for @ fill <from:block_pos> <to:block_pos> <block:block_predicate> replace <filter:block_predicate>: 1
- Failed to parse any options @ execute: 1

## confirmed findings (first 20)
- [final] `blocks[nether].columns.0,3`: base="b2aa15dda136420f8037ea181822e5bc3b9394300b6b058e1be6afcd8157b76f" | mcfc="bf6cf80d747fd6b6fcc03cd6de8de057482dcceb6551bf4babd23aa7b8b0b154"
- [gen] `blocks[nether].columns.0,3`: base="b2aa15dda136420f8037ea181822e5bc3b9394300b6b058e1be6afcd8157b76f" | mcfc="bf6cf80d747fd6b6fcc03cd6de8de057482dcceb6551bf4babd23aa7b8b0b154"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-7,7`
- [final] `blocks[overworld].columns.3,6`
- [final] `blocks[overworld].columns.3,7`
- [final] `blocks[overworld].columns.5,6`
- [gen] `blocks[overworld].columns.-7,6`
- [gen] `blocks[overworld].columns.-7,7`
- [gen] `blocks[overworld].columns.3,6`
- [gen] `blocks[overworld].columns.3,7`
- [gen] `blocks[overworld].columns.5,6`
- [gen] `blocks[overworld].columns.5,7`

## low-confidence findings (11, first 10; not counted)
- `blocks[overworld].columns.-7,6`
- `blocks[overworld].columns.5,7`
- `blocks[overworld].columns.6,2`
- `entities.minecraft:overworld.list[minecraft:item#14].customName`
- `entities.minecraft:overworld.list[minecraft:item#14].nbt`
- `entities.minecraft:overworld.list[minecraft:item#14].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#14].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#14].pos[2]`
- `entities.minecraft:overworld.list[minecraft:item#14].tags[]`
- `entities.minecraft:overworld.list[minecraft:item#14].type`
