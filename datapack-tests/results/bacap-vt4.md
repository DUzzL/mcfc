# bacap-vt4: OK

- kind: function, packs: bacap, vt-multiplayer-sleep, vt-afk-display, vt-coordinates-hud, vt-homes
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 5537/5537 functions compiled (100.0%), partial fallback commands: 119
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 122, noise paths 2437; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 19.75 ms (min 19.67, max 19.99); mcfc median 20.43 ms (min 19.93, max 20.66)
- mcfc debug: 5452 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.0074
- exerciser (mcfc): ran 5503 of 5537 candidate functions (macro skipped 8, name skipped 23, scenario skipped 3), errors 57, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Unknown command: bossbar: 15
- Failed to parse any options @ scoreboard objectives: 8
- Unknown command: forceload: 3
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Failed to parse any options @ execute: 1

## low-confidence findings (122, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#110].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#111].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#112].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#113].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#114].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#483].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#484].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#534].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#535].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#536].pos[0]`
