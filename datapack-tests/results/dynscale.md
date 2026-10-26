# dynscale: DIFF

- kind: function, packs: dynscale
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 3/3 functions compiled (100.0%), partial fallback commands: 2
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 4, flaky 0, low-confidence 0, noise paths 2; confirmation disabled
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- mcfc debug: 2 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 0 of 3 candidate functions (macro skipped 0, name skipped 0, scenario skipped 3), errors 0, slow 0

## top partial-fallback reasons
- Unknown command: attribute: 2

## confirmed findings (first 20)
- [final] `scoreboard.scores.DPTester1.other`: base=205 | mcfc=207
- [final] `scoreboard.scores.DPTester1.size_time`: base=<missing> | mcfc=1000
- [final] `scoreboard.scores.DPTester2.other`: base=205 | mcfc=207
- [final] `scoreboard.scores.DPTester2.size_time`: base=<missing> | mcfc=1000
