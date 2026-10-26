# mr-veinminer: FALLBACK-ISSUE

- kind: function, packs: mr-veinminer
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 27/33 functions compiled (81.8%), partial fallback commands: 36
- whole-function fallbacks: 0, compile failures: 6, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 3; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.33 ms (min 0.27, max 0.35); mcfc median 0.27 ms (min 0.26, max 0.42)
- mcfc debug: 16 compiled | 8 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.1126
- exerciser (mcfc): ran 15 of 33 candidate functions (macro skipped 16, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 27
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 4
- Macro commands are not supported for json arguments: 4
- Unknown command: say: 1
