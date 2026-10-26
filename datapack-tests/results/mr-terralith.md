# mr-terralith: DIFF

- kind: worldgen, packs: mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 3/3 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 3, flaky 50, low-confidence 10, noise paths 1292; worldgen (gen dump) findings 11
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.42 ms (min 1.23, max 1.65); mcfc median 1.16 ms (min 0.92, max 1.4)
- mcfc debug: 3 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 3 of 3 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: spreadplayers: 1

## confirmed findings (first 20)
- [gen] `blocks[overworld].columns.-2,-1`: base="a41eeebade771eed7fb60b3d8a0a6e7f32ee4728dea8a1c8e085d100dbe3d314" | mcfc="4e1ac88a74c552f73988108d716b23dd29a50c98a62815dd54a172c27cc0bbb7"
- [gen] `blocks[overworld].columns.-2,0`: base="0b78f674159ad30b66e636b840988fd39bfcf03dc45ef6a0200c8be2cb76eb19" | mcfc="0c9393566242881bbcd5014c60a035cf6b8aa4b61e854256ed029f839d9a5e83"
- [gen] `blocks[overworld].columns.-3,-1`: base="2b94955294f260b7e3790ce33a501d9adaa5bb26f7577a7486b5c9456b98ea01" | mcfc="fb026db0fb3631512013fa4797710cf661e69f8fcd6eae8bad1928c4d460ca88"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.3,-6`
- [final] `entities.minecraft:overworld.counts.minecraft:drowned`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].customName`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].type`
- [final] `entities.minecraft:overworld.list[minecraft:drowned#1].uuid`

## low-confidence findings (10, first 10; not counted)
- `blocks[overworld].columns.-2,-1`
- `blocks[overworld].columns.-2,0`
- `blocks[overworld].columns.-2,1`
- `blocks[overworld].columns.-3,-1`
- `blocks[overworld].columns.-3,0`
- `blocks[overworld].columns.-4,2`
- `blocks[overworld].columns.-5,1`
- `blocks[overworld].columns.-5,2`
- `blocks[overworld].columns.-5,3`
- `blocks[overworld].columns.1,2`
