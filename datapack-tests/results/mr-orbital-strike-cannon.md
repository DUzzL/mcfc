# mr-orbital-strike-cannon: DIFF

- kind: function, packs: mr-orbital-strike-cannon
- mcfc final state: READY_ON (after load: READY_ON)
- compile: 88/88 functions compiled (100.0%), partial fallback commands: 717
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 31, flaky 84, low-confidence 0, noise paths 12016
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 9.12 ms (min 8.87, max 9.18); mcfc median 7.93 ms (min 7.86, max 8.42)
- mcfc debug: 55 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 55 of 88 candidate functions (macro skipped 0, name skipped 1, scenario skipped 32), errors 0, slow 0
- note: mr-orbital-strike-cannon: pack_format patched (manifest): pack_format 108 -> 121; added min_format/max_format [121, 0] (required for formats above 81)

## top partial-fallback reasons
- Expected DoubleLiteral nbt value, but got IntegerLiteral: 709
- Missing space after argument @ execute if predicate <predicate:resource_location>: 6
- Expected IntegerLiteral nbt value, but got DoubleLiteral: 1
- Expected a selector: 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#1036].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1083].pos[1]`: base=-63.0 | mcfc=20604.78
- [final] `entities.minecraft:overworld.list[minecraft:item#1160].pos[1]`: base=-63.0 | mcfc=-61.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1235].pos[1]`: base=-63.0 | mcfc=-61.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1309].pos[1]`: base=-63.0 | mcfc=-61.0
- [final] `entities.minecraft:overworld.list[minecraft:item#131].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1386].pos[1]`: base=-63.0 | mcfc=-61.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1481].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1489].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#1501].pos[1]`: base=-63.0 | mcfc=20604.78
- [final] `entities.minecraft:overworld.list[minecraft:item#1522].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#155].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#204].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#253].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#276].pos[1]`: base=-63.0 | mcfc=-60.41
- [final] `entities.minecraft:overworld.list[minecraft:item#399].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#414].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#436].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#489].pos[1]`: base=-63.0 | mcfc=-62.0
- [final] `entities.minecraft:overworld.list[minecraft:item#4].pos[1]`: base=-63.0 | mcfc=-61.0

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#1052].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1060].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1090].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1097].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1099].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1110].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1143].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#114].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1164].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1184].pos[1]`

## Cache restart
Fresh server dir (runs\_cache_test), pack installed, same dir started twice.
- start 1: `Cache miss: no entry for 67b9fa08...`, then `Saved 88 compiled functions across 36 batch class(es) to cache (0 vanilla-fallback)` and `[Summary] 88 function(s) found, 88 parsed, 88 compiled, 88 registered`; state READY_ON, 88/88.
- start 2 (pack still installed): `Cache hit - loading 88 functions across 36 batch class(es) from disk (skipping compilation)`, `Loaded compiled functions from cache in 261ms`, `[Summary] 88 function(s) found, 88 parsed, 88 compiled (from cache)`; state READY_ON immediately, 88/88.
- no `Cache read failed`, no ERROR from the compiler in either log (only the unrelated `Perflib 009` / `No key layers` lines).
- pack functions run via RCON after the restart (`orbital_strike_cannon:load`, `get/get_nuke`, `get/get_stab`, `get/get_stasis_1`): no `threw; running it through vanilla` in the log.

## Scenario note
`scenarios/mr-orbital-strike-cannon.json` skips the payload nuke/lawnuke/leowook/wither functions in the exerciser. Without it, the 200-tick step after the exerciser did not finish within 600 s in vanilla (and in mcfc): the payloads spawn about 1100 TNT entities and the server fell about 3800 ticks behind.
