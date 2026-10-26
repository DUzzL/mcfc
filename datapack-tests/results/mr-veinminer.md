# mr-veinminer: FALLBACK-ISSUE

- kind: function, packs: mr-veinminer
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 26/33 functions compiled (78.8%), partial fallback commands: 37
- whole-function fallbacks: 0, compile failures: 7, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 3; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.31 ms (min 0.28, max 0.35); mcfc median 0.3 ms (min 0.28, max 0.35)
- mcfc debug: 16 compiled | 9 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.1123
- exerciser (mcfc): ran 15 of 33 candidate functions (macro skipped 16, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 28
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 4
- Macro commands are not supported for json arguments: 4
- Unknown command: say: 1

## Investigate
- No diffs. Compile failures are all "`return` inside an unparsed command would be swallowed; falling back the whole function" (see list above); scenario drives the pack as a fake survival player by setting the pack's mined/used objectives and removing the block by hand.
