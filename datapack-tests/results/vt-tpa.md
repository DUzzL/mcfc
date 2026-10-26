# vt-tpa: DIFF

- kind: function, packs: vt-tpa
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 19/19 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 4, flaky 0, low-confidence 0, noise paths 4
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.15 ms (min 0.15, max 0.18); mcfc median 0.17 ms (min 0.16, max 0.17)
- mcfc debug: 18 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 18 of 19 candidate functions (macro skipped 0, name skipped 1, scenario skipped 0), errors 0, slow 0

## confirmed findings (first 20)
- [final] `scheduled.events[0].trigger_time`: base=6484 | mcfc=6488
- [final] `scheduled.events[1].trigger_time`: base=6503 | mcfc=6507
- [final] `world.dayTime`: base=6483 | mcfc=6487
- [final] `world.gameTime`: base=6483 | mcfc=6487
