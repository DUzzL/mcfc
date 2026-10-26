# mr-veinminer: OK

- kind: function, packs: mr-veinminer
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 33/33 functions compiled (100.0%), partial fallback commands: 36
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 1; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.34 ms (min 0.29, max 0.41); mcfc median 0.29 ms (min 0.25, max 0.36)
- mcfc debug: 16 compiled | 6 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9042
- exerciser (mcfc): ran 15 of 33 candidate functions (macro skipped 16, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 27
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 4
- Macro commands are not supported for json arguments: 4
- Unknown command: say: 1
