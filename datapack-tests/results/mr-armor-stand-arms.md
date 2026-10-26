# mr-armor-stand-arms: OK

- kind: function, packs: mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 20/20 functions compiled (100.0%), partial fallback commands: 7
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 0; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.33 ms (min 0.31, max 0.79); mcfc median 0.42 ms (min 0.27, max 0.72)
- mcfc debug: 13 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 13 of 20 candidate functions (macro skipped 5, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 5
- Unknown command: dialog: 1
- Unknown command: say: 1
