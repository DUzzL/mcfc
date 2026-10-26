# mr-gardeners-dream: OK

- kind: function, packs: mr-gardeners-dream
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-cd77d43f.jar sha256=b2a4923b93f4217a26c275c3c03f8f52795cc15b052ef828ec3841ad5690161b (file mtime 2026-10-26 09:06:07)
- compile: 54/54 functions compiled (100.0%), partial fallback commands: 23
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 4, noise paths 22; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.23 ms (min 0.18, max 0.29); mcfc median 0.24 ms (min 0.19, max 0.3)
- mcfc debug: 36 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 53 of 54 candidate functions (macro skipped 0, name skipped 0, scenario skipped 1), errors 32, slow 0

## top partial-fallback reasons
- Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate>: 20
- Unknown command: msg: 2
- Expected a selector: 1

## low-confidence findings (4, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#2].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#3].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#3].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#3].pos[2]`
