# mr-tree-vein-miner: FALLBACK-ISSUE

- kind: function, packs: mr-tree-vein-miner
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 64/70 functions compiled (91.4%), partial fallback commands: 9
- whole-function fallbacks: 0, compile failures: 6, runtime disables: 0
- findings: confirmed 0, flaky 1, low-confidence 0, noise paths 194
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.35 ms (min 0.27, max 0.37); mcfc median 0.33 ms (min 0.25, max 0.35)
- mcfc debug: 29 compiled | 10 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.2187
- exerciser (mcfc): ran 53 of 70 candidate functions (macro skipped 12, name skipped 5, scenario skipped 0), errors 32, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 9

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#30].pos[1]`

## Investigate
- No diffs. Compile failures are all "`return` inside an unparsed command would be swallowed; falling back the whole function" (see list above); scenario drives the pack as a fake survival player by setting the pack's mined/used objectives and removing the block by hand.
