# mr-tectonic: OK

- kind: worldgen, packs: mr-tectonic
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 0/0 functions compiled, partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 1, low-confidence 1, noise paths 260; worldgen (gen dump) findings 1
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.52 ms (min 0.51, max 0.99); mcfc median 0.43 ms (min 0.41, max 0.88)
- exerciser (mcfc): ran 0 of 0 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## flaky findings (first 10, not reproduced in the second attempt)
- [gen] `blocks[overworld].columns.6,7`

## low-confidence findings (1, first 10; not counted)
- `blocks[overworld].columns.6,7`
