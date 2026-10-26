# vt-durability-ping: OK

- kind: function, packs: vt-durability-ping
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 106/106 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 4; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.24 ms (min 0.21, max 0.29); mcfc median 0.23 ms (min 0.21, max 0.27)
- mcfc debug: 101 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 101 of 106 candidate functions (macro skipped 0, name skipped 5, scenario skipped 0), errors 0, slow 0
