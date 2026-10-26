# mr-functions-all: FALLBACK-ISSUE

- kind: function, packs: bacap, mr-veinminer, mr-tree-vein-miner, mr-timber-strike, mr-gardeners-dream, mr-spawn-animations, mr-orbital-strike-cannon, mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 5829/5839 functions compiled (99.8%), partial fallback commands: 262
- whole-function fallbacks: 0, compile failures: 10, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 12, noise paths 2640; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 17.91 ms (min 17.65, max 18.18); mcfc median 20.33 ms (min 19.56, max 20.69)
- mcfc debug: 5467 compiled | 8 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8269
- exerciser (mcfc): ran 5726 of 5839 candidate functions (macro skipped 50, name skipped 30, scenario skipped 33), errors 274, slow 0
- note: mr-orbital-strike-cannon: pack_format patched (manifest): pack_format 108 -> 121; added min_format/max_format [121, 0] (required for formats above 81)

## top partial-fallback reasons
- Macros are not supported in this location: 105
- Missing space after argument @ function <functions:function>: 73
- Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate>: 20
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Expected a selector: 17
- Failed to parse any options @ scoreboard objectives: 8
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 5
- Unknown command: clone: 4
- Macro commands are not supported for json arguments: 4
- Unknown command: say: 4
- Unknown command: attribute: 2
- Unknown command: msg: 2
- Unknown command: dialog: 1

## low-confidence findings (12, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#316].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#719].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#720].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#740].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#741].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#742].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#743].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#744].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#745].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#746].pos[0]`
