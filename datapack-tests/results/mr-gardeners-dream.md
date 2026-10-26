# mr-gardeners-dream: OK

- kind: function, packs: mr-gardeners-dream
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 54/54 functions compiled (100.0%), partial fallback commands: 23
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 5, low-confidence 0, noise paths 37
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.23 ms (min 0.19, max 0.29); mcfc median 0.21 ms (min 0.2, max 0.3)
- mcfc debug: 36 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 53 of 54 candidate functions (macro skipped 0, name skipped 0, scenario skipped 1), errors 32, slow 0

## top partial-fallback reasons
- Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate>: 20
- Unknown command: msg: 2
- Expected a selector: 1

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#5].customName`
- [final] `entities.minecraft:overworld.list[minecraft:item#5].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#5].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:item#5].type`
- [final] `entities.minecraft:overworld.list[minecraft:item#5].uuid`

## Investigate
- No diffs. Partial fallback "Failed to parse any options @ setblock <pos> <block_predicate>" (20 cmds) likely `setblock ~ ~ ~ mossy_cobblestone_slab[type=bottom] replace` (`hit_cauldron.mcfunction:9`, `hit_cauldron_upper.mcfunction:6`; log has counts only, not verified per line).
- Flaky only: item#5 entity-order shift (items), not reproduced in attempt 2.
