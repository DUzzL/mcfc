# mr-functions-all: FALLBACK-ISSUE

- kind: function, packs: bacap, mr-veinminer, mr-tree-vein-miner, mr-timber-strike, mr-gardeners-dream, mr-spawn-animations, mr-orbital-strike-cannon, mr-armor-stand-arms
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 5829/5839 functions compiled (99.8%), partial fallback commands: 262
- whole-function fallbacks: 0, compile failures: 10, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 366, noise paths 2413; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 17.46 ms (min 17.39, max 18.16); mcfc median 19.17 ms (min 18.63, max 20.84)
- mcfc debug: 5467 compiled | 8 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8266
- exerciser (mcfc): ran 5759 of 5839 candidate functions (macro skipped 50, name skipped 30, scenario skipped 0), errors 307, slow 0
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

## low-confidence findings (366, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#177].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#194].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#195].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#196].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#197].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#198].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#212].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#213].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#214].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#215].pos[0]`
