# mr-functions-all: OK

- kind: function, packs: bacap, mr-veinminer, mr-tree-vein-miner, mr-timber-strike, mr-gardeners-dream, mr-spawn-animations, mr-orbital-strike-cannon, mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 5839/5839 functions compiled (100.0%), partial fallback commands: 262
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 11, noise paths 2631; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 23.88 ms (min 23.39, max 29.54); mcfc median 21.07 ms (min 20.85, max 21.96)
- mcfc debug: 5467 compiled | 4 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9443
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

## low-confidence findings (11, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#138].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#139].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#140].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#4].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#728].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#790].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#791].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#792].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#793].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#794].pos[0]`
