# vt-armor-statues: OK

- kind: function, packs: vt-armor-statues
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 72/72 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 1, low-confidence 0, noise paths 27
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.4 ms (min 0.21, max 0.47); mcfc median 0.31 ms (min 0.23, max 0.38)
- mcfc debug: 69 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 69 of 72 candidate functions (macro skipped 1, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Only one entity is allowed, but the provided selector allows more than one: 1

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `scoreboard.scores.#no_crossing.as_angle`
