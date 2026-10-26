# vt-armor-statues: OK

- kind: function, packs: vt-armor-statues
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 72/72 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 10; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.25 ms (min 0.21, max 0.43); mcfc median 0.29 ms (min 0.19, max 0.38)
- mcfc debug: 69 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 69 of 72 candidate functions (macro skipped 1, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Only one entity is allowed, but the provided selector allows more than one: 1
