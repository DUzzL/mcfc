# vt-homes: OK

- kind: function, packs: vt-homes
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 54/54 functions compiled (100.0%), partial fallback commands: 4
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 4; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.23 ms (min 0.2, max 0.29); mcfc median 0.21 ms (min 0.17, max 0.27)
- mcfc debug: 19 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 47 of 54 candidate functions (macro skipped 0, name skipped 4, scenario skipped 3), errors 31, slow 0

## top partial-fallback reasons
- Unknown command: forceload: 3
- Failed to parse any options @ execute: 1
