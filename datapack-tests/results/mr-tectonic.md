# mr-tectonic: DIFF

- kind: worldgen, packs: mr-tectonic
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 0/0 functions compiled, partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 4, flaky 16, low-confidence 13, noise paths 262; worldgen (gen dump) findings 16
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.46 ms (min 0.43, max 0.84); mcfc median 0.45 ms (min 0.44, max 0.77)
- exerciser (mcfc): ran 0 of 0 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## confirmed findings (first 20)
- [gen] `blocks[overworld].columns.-1,-3`: base="3699bd823b899cebbc39cc1cfcc4e320e025807b2fc56999c22c305ba7995de5" | mcfc="1dd9ed8e267a02a48bdc43aed9b0457a87eb83b6f05a6c6aaa284179fbbd159b"
- [gen] `blocks[overworld].columns.0,-4`: base="d13476abaa77c8c72f2602b7c3774f44a1c52cba58179f025b0a7520df043785" | mcfc="4fd6a569c3f3b5911404957f936181018ad8fc1a5aff4abba10de35f7945d8aa"
- [gen] `blocks[overworld].columns.0,-5`: base="1e74eba011e8c2828426f9ce5d2e60f65f6c1cc1acb9a2d8bc75fd951ef7ffbb" | mcfc="4c6a5dcf092a29d4201130ebd0e1eae1b2f4ed772046a3dadec8da74fd2d2008"
- [gen] `blocks[overworld].columns.1,4`: base="f1c84b0d6aad1c1a8e390a67bc50953369dc3ed5c917317316b49ee74763d0f0" | mcfc="b47c423b8905baac35d045fdabd3202922fa80dfbc2cf6ce07dadaf62d1b34af"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.0,0`
- [final] `blocks[overworld].columns.1,0`
- [final] `blocks[overworld].columns.2,0`
- [final] `blocks[overworld].columns.2,1`
- [gen] `blocks[overworld].columns.-1,-1`
- [gen] `blocks[overworld].columns.-1,-2`
- [gen] `blocks[overworld].columns.-2,-7`
- [gen] `blocks[overworld].columns.0,0`
- [gen] `blocks[overworld].columns.1,-1`
- [gen] `blocks[overworld].columns.1,-2`

## low-confidence findings (13, first 10; not counted)
- `blocks[overworld].columns.-1,-1`
- `blocks[overworld].columns.-1,-2`
- `blocks[overworld].columns.-1,-3`
- `blocks[overworld].columns.-1,-4`
- `blocks[overworld].columns.0,-4`
- `blocks[overworld].columns.0,-5`
- `blocks[overworld].columns.0,-6`
- `blocks[overworld].columns.1,-1`
- `blocks[overworld].columns.1,-2`
- `blocks[overworld].columns.1,4`
