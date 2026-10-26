# _counter: OK

- kind: function, packs: _counter
- mcfc final state: READY_ON (after load: READY_ON)
- compile: 4/4 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 2; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.26 ms (min 0.2, max 0.35); mcfc median 0.22 ms (min 0.2, max 0.31)
- mcfc debug: 3 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 3 of 4 candidate functions (macro skipped 1, name skipped 0, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: say: 1
