# mr-structory: DIFF

- kind: worldgen, packs: mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- compile: 1/1 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 1, flaky 26, low-confidence 9, noise paths 320; worldgen (gen dump) findings 14
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.63 ms (min 0.56, max 1.08); mcfc median 0.74 ms (min 0.61, max 1.05)
- mcfc debug: 1 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 1 of 1 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## confirmed findings (first 20)
- [gen] `blocks[overworld].columns.-4,-6`: base="756663c78e543e360d346cef57ad3960194a2282b7468f858a39c6822af8790d" | mcfc="d6d9c6622065ce981dee28f5cd897da294b94084667adad7762f4fcdee731ef4"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-4,6`
- [final] `blocks[overworld].columns.-5,7`
- [final] `blocks[overworld].columns.4,1`
- [final] `blocks[overworld].columns.6,4`
- [final] `blocks[overworld].columns.6,5`
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].customName`
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#5].pos[2]`

## low-confidence findings (9, first 10; not counted)
- `blocks[overworld].columns.-1,5`
- `blocks[overworld].columns.-4,-6`
- `blocks[overworld].columns.0,-8`
- `blocks[overworld].columns.3,-2`
- `blocks[overworld].columns.4,-2`
- `blocks[overworld].columns.4,-3`
- `blocks[overworld].columns.4,0`
- `blocks[overworld].columns.6,2`
- `blocks[overworld].columns.6,6`
