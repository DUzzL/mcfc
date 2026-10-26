# vt-armor-statues: DIFF

- kind: function, packs: vt-armor-statues
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 72/72 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 8, flaky 0, low-confidence 0, noise paths 28
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.26 ms (min 0.19, max 0.31); mcfc median 0.23 ms (min 0.19, max 0.41)
- mcfc debug: 69 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 69 of 72 candidate functions (macro skipped 1, name skipped 2, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Only one entity is allowed, but the provided selector allows more than one: 1

## confirmed findings (first 20)
- [final] `scoreboard.scores.<armor_stand#1>.as_edited_uuid1`: base=1012973762 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_edited_uuid2`: base=1023752607 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_edited_uuid3`: base=-1074035122 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_edited_uuid4`: base=-913057229 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_lock_uuid1`: base=1012973762 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_lock_uuid2`: base=1023752607 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_lock_uuid3`: base=-1074035122 | mcfc=0
- [final] `scoreboard.scores.<armor_stand#1>.as_lock_uuid4`: base=-913057229 | mcfc=0
