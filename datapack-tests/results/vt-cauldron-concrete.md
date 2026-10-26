# vt-cauldron-concrete: OK

- kind: function, packs: vt-cauldron-concrete
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 2/2 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 1, low-confidence 0, noise paths 31
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.25 ms (min 0.2, max 0.47); mcfc median 0.23 ms (min 0.19, max 0.45)
- mcfc debug: 1 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 1 of 2 candidate functions (macro skipped 0, name skipped 1, scenario skipped 0), errors 0, slow 0

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#7].pos[0]`
