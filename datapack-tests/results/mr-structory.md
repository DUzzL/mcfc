# mr-structory: DIFF

- kind: worldgen, packs: mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 1/1 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 3, flaky 3, low-confidence 1, noise paths 338; worldgen (gen dump) findings 4
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.62 ms (min 0.54, max 0.99); mcfc median 0.69 ms (min 0.53, max 1.17)
- mcfc debug: 1 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 1 of 1 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.7,-3`: base="7c7f8024c2514cd9ea92f766dc63c358b53e840356bb21e79ae11fdf143b8e25" | mcfc="5ee5643a464b7d4c48b9ff5d9f4e75294a8531ca451469f9453895bd125f83a3"
- [gen] `blocks[overworld].columns.7,-3`: base="7c7f8024c2514cd9ea92f766dc63c358b53e840356bb21e79ae11fdf143b8e25" | mcfc="5ee5643a464b7d4c48b9ff5d9f4e75294a8531ca451469f9453895bd125f83a3"
- [gen] `blocks[overworld].columns.7,-4`: base="74c6860e00509661c68052560bc47cd63b4911e58882ea45b49fac3e635c067b" | mcfc="f1f2609ea872bcd5edc2295d6f143e120c8c339dc205b2ddbdbde4ded1e9101f"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.3,3`
- [gen] `blocks[overworld].columns.3,3`
- [gen] `blocks[overworld].columns.7,-5`

## low-confidence findings (1, first 10; not counted)
- `blocks[overworld].columns.7,-4`
