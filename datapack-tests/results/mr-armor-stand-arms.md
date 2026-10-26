# mr-armor-stand-arms: FALLBACK-ISSUE

- kind: function, packs: mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 19/20 functions compiled (95.0%), partial fallback commands: 7
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 2
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.28 ms (min 0.2, max 0.53); mcfc median 0.24 ms (min 0.19, max 0.49)
- mcfc debug: 13 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9895
- exerciser (mcfc): ran 13 of 20 candidate functions (macro skipped 5, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 5
- Unknown command: dialog: 1
- Unknown command: say: 1
