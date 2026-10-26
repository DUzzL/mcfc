# mr-timber-strike: FALLBACK-ISSUE

- kind: function, packs: mr-timber-strike
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 46/51 functions compiled (90.2%), partial fallback commands: 95
- whole-function fallbacks: 0, compile failures: 5, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 121
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.3 ms (min 0.3, max 0.37); mcfc median 0.3 ms (min 0.26, max 0.38)
- mcfc debug: 41 compiled | 7 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.7739
- exerciser (mcfc): ran 41 of 51 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 79
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1

## Investigate
- No diffs. Compile failures are all "`return` inside an unparsed command would be swallowed; falling back the whole function" (see list above); scenario drives the pack as a fake survival player by setting the pack's mined/used objectives and removing the block by hand.
