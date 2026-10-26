# vt-multiplayer-sleep: OK

- kind: function, packs: vt-multiplayer-sleep
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 33/33 functions compiled (100.0%), partial fallback commands: 17
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 3; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.28 ms (min 0.25, max 0.34); mcfc median 0.26 ms (min 0.25, max 0.31)
- mcfc debug: 28 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 26 of 33 candidate functions (macro skipped 0, name skipped 7, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: bossbar: 15
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
