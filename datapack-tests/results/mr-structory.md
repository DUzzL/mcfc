# mr-structory: OK

- kind: worldgen, packs: mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 1/1 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 4, low-confidence 3, noise paths 328; worldgen (gen dump) findings 3
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.67 ms (min 0.57, max 1.13); mcfc median 0.61 ms (min 0.53, max 1.08)
- mcfc debug: 1 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 1 of 1 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-8,2`
- [gen] `blocks[overworld].columns.-5,3`
- [gen] `blocks[overworld].columns.-5,4`
- [gen] `blocks[overworld].columns.-8,2`

## low-confidence findings (3, first 10; not counted)
- `blocks[overworld].columns.-5,3`
- `blocks[overworld].columns.-5,4`
- `blocks[overworld].columns.6,-8`
