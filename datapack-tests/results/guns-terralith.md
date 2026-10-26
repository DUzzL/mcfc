# guns-terralith: DIFF

- kind: worldgen, packs: mr-timber-strike, mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 53/54 functions compiled (98.2%), partial fallback commands: 84
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 3, flaky 9, low-confidence 6, noise paths 1343; worldgen (gen dump) findings 9
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 3.15 ms (min 2.62, max 3.37); mcfc median 2.1 ms (min 1.98, max 2.58)
- mcfc debug: 44 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.996
- exerciser (mcfc): ran 44 of 54 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 67
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1
- Unknown command: spreadplayers: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.4,-8`: base="c14a3e82c26a1cdc6e3811c6541556778d14644f9659ddfa5632c9ef3df56341" | mcfc="1fc3faba92d6edb39d6c2643986289660cccb7468c7e06398e277afa9036c839"
- [gen] `blocks[overworld].columns.-1,3`: base="51b5e03f6cd929443406a5aa460029a3a9a5e33442a42f97dd0c0dd0aa874ba9" | mcfc="887321f92d69a0216a33a22bf142178496a54703a8dfee8e84e6c3e202723ad6"
- [gen] `blocks[overworld].columns.4,-8`: base="c14a3e82c26a1cdc6e3811c6541556778d14644f9659ddfa5632c9ef3df56341" | mcfc="1fc3faba92d6edb39d6c2643986289660cccb7468c7e06398e277afa9036c839"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.4,1`
- [final] `blocks[overworld].columns.4,2`
- [gen] `blocks[overworld].columns.-1,2`
- [gen] `blocks[overworld].columns.3,2`
- [gen] `blocks[overworld].columns.3,3`
- [gen] `blocks[overworld].columns.3,5`
- [gen] `blocks[overworld].columns.4,1`
- [gen] `blocks[overworld].columns.4,2`
- [gen] `blocks[overworld].columns.4,5`

## low-confidence findings (6, first 10; not counted)
- `blocks[overworld].columns.-1,2`
- `blocks[overworld].columns.-1,3`
- `blocks[overworld].columns.3,2`
- `blocks[overworld].columns.3,3`
- `blocks[overworld].columns.3,5`
- `blocks[overworld].columns.4,5`
