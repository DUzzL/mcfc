# mr-dungeons-and-taverns: DIFF

- kind: worldgen, packs: mr-dungeons-and-taverns
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 65/65 functions compiled (100.0%), partial fallback commands: 238
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 7, flaky 17, low-confidence 10, noise paths 363; worldgen (gen dump) findings 15
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.48 ms (min 0.43, max 0.85); mcfc median 0.49 ms (min 0.39, max 0.63)
- mcfc debug: 5 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 65 of 65 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 65, slow 0

## top partial-fallback reasons
- Missing space after argument @ execute if predicate <predicate:resource_location>: 226
- Unknown command: attribute: 5
- Missing space after argument @ execute unless predicate <predicate:resource_location>: 3
- Unknown command: ride: 2
- Cannot turn "{"atlas":"minecraft:items","sprite":"dnt:item/16x_logo"}" into Text: 1
- Unknown command: damage: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.-4,-4`: base="c5e8b949c13d23e11d4054911466d3b1cc2114197eb89ee752efe2345c46d81c" | mcfc="bb1820e2332115fa715d6318f9f688c571ac6df6726611fe26d13227b1af8c79"
- [final] `blocks[overworld].columns.-4,-5`: base="2854ed70664cf3ec24de4ec8807e3a7cb71b421428686611a6bc0ff4970e5939" | mcfc="5adea25d7afc3a2120c44faa4b58aa04abe30da9c10e7ec5f561e6a0af87ed23"
- [final] `blocks[overworld].columns.-5,-5`: base="dca65399e23b37041b168a893a9a397aa312bbf2fe0f4fbc92fbf6eb20468245" | mcfc="04fb6d2e4ba2392dffb6a9e46fb3921d5a42d8eee61b63ab6e31c14f99154a50"
- [gen] `blocks[overworld].columns.-4,-4`: base="c5e8b949c13d23e11d4054911466d3b1cc2114197eb89ee752efe2345c46d81c" | mcfc="bb1820e2332115fa715d6318f9f688c571ac6df6726611fe26d13227b1af8c79"
- [gen] `blocks[overworld].columns.-4,-5`: base="2854ed70664cf3ec24de4ec8807e3a7cb71b421428686611a6bc0ff4970e5939" | mcfc="5adea25d7afc3a2120c44faa4b58aa04abe30da9c10e7ec5f561e6a0af87ed23"
- [gen] `blocks[overworld].columns.-5,-5`: base="dca65399e23b37041b168a893a9a397aa312bbf2fe0f4fbc92fbf6eb20468245" | mcfc="04fb6d2e4ba2392dffb6a9e46fb3921d5a42d8eee61b63ab6e31c14f99154a50"
- [gen] `blocks[overworld].columns.0,-2`: base="cf46750e1016f6b1607f467bac45144d5773e6ef9f8878e6626e980336e2d525" | mcfc="0f43193fe56c6bf9333d1327c7fe9755585093f04baf4a26f728c024dc980e32"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[nether].columns.-3,-1`
- [final] `blocks[overworld].columns.-2,-4`
- [final] `blocks[overworld].columns.-3,-4`
- [final] `blocks[overworld].columns.1,3`
- [final] `blocks[overworld].columns.6,3`
- [final] `blocks[overworld].columns.7,7`
- [gen] `blocks[nether].columns.-3,-1`
- [gen] `blocks[overworld].columns.-2,-4`
- [gen] `blocks[overworld].columns.-3,-4`
- [gen] `blocks[overworld].columns.1,3`

## low-confidence findings (10, first 10; not counted)
- `blocks[overworld].columns.0,-2`
- `blocks[overworld].columns.4,-5`
- `blocks[overworld].columns.4,1`
- `blocks[overworld].columns.4,2`
- `blocks[overworld].columns.6,2`
- `blocks[overworld].columns.7,6`
- `entities.minecraft:overworld.list[minecraft:item#13].customName`
- `entities.minecraft:overworld.list[minecraft:item#13].tags[]`
- `entities.minecraft:overworld.list[minecraft:item#13].type`
- `entities.minecraft:overworld.list[minecraft:item#13].uuid`
