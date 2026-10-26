# unsupported commands (mcfc diagnostics)

837 diagnostics from 25 individual packs (combinations excluded). Full aggregation: `_unsupported.json`.

## (i) reasons

| reason | commands | packs |
|---|---|---|
| PARSE_ERROR: Unknown command: tell | 204 | 1: mr-dungeons-and-taverns (204) |
| PARSE_ERROR: Macros are not supported in this location | 140 | 7: mr-timber-strike (67), vt-graves (30), mr-veinminer (27), mr-tree-vein-miner (6) |
| PARSE_ERROR: Failed to parse any options @ execute | 91 | 3: mr-incendium (89), mr-nullscape (1), vt-homes (1) |
| PARSE_ERROR: Missing space after argument @ function <functions:function> | 73 | 1: bacap (73) |
| PARSE_ERROR: Unknown command: attribute | 63 | 3: mr-incendium (56), mr-dungeons-and-taverns (5), mr-spawn-animations (2) |
| PARSE_ERROR: Unknown command: rotate | 61 | 2: mr-incendium (50), vt-graves (11) |
| PARSE_ERROR: Unknown command: bossbar | 23 | 2: vt-multiplayer-sleep (15), mr-incendium (8) |
| PARSE_ERROR: Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate> | 20 | 1: mr-gardeners-dream (20) |
| PARSE_ERROR: Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advance | 17 | 1: bacap (17) |
| PARSE_ERROR: Expected a selector | 17 | 3: mr-timber-strike (15), mr-gardeners-dream (1), mr-orbital-strike-cannon (1) |
| PARSE_ERROR: Invalid integer | 17 | 2: mr-incendium (15), vt-graves (2) |
| COMPILE_ERROR: `return` inside an unparsed command would be swallowed; falling back the whole function | 16 | 7: mr-veinminer (6), vt-graves (3), mr-tree-vein-miner (2), mr-incendium (2) |
| PARSE_ERROR: Unknown command: place | 13 | 2: mr-nullscape (12), mr-incendium (1) |
| PARSE_ERROR: Unknown command: forceload | 12 | 4: mr-incendium (5), vt-homes (3), mr-nullscape (2), vt-graves (2) |
| PARSE_ERROR: Unknown command: damage | 12 | 2: mr-incendium (11), mr-dungeons-and-taverns (1) |
| PARSE_ERROR: Failed to parse any options @ scoreboard objectives | 8 | 1: bacap (8) |
| PARSE_ERROR: Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word> | 7 | 1: mr-incendium (7) |
| PARSE_ERROR: Macro commands are not supported for json arguments | 5 | 2: mr-veinminer (4), vt-graves (1) |
| PARSE_ERROR: Too many arguments for @ scoreboard players reset <targets:score_holder> | 5 | 2: mr-veinminer (4), mr-timber-strike (1) |
| PARSE_ERROR: Unknown command: say | 4 | 3: mr-spawn-animations (2), mr-veinminer (1), mr-armor-stand-arms (1) |
| PARSE_ERROR: Unknown command: clone | 4 | 1: mr-spawn-animations (4) |
| PARSE_ERROR: Unknown command: spreadplayers | 4 | 2: mr-incendium (3), mr-terralith (1) |
| PARSE_ERROR: Failed to parse any options @ execute store <store_location:result_store_location_type> | 4 | 2: mr-incendium (2), vt-multiplayer-sleep (2) |
| PARSE_ERROR: Unknown command: ride | 4 | 2: mr-dungeons-and-taverns (2), vt-graves (2) |
| PARSE_ERROR: Unknown command: msg | 2 | 1: mr-gardeners-dream (2) |
| ... 9 more reasons in the json | | |

## (ii) failing command word

| command | count | top reason |
|---|---|---|
| `tell` | 204 | PARSE_ERROR: Unknown command: tell (204) |
| `execute run function` | 74 | PARSE_ERROR: Missing space after argument @ function <functions:function> (72) |
| `attribute` | 63 | PARSE_ERROR: Unknown command: attribute (63) |
| `rotate` | 61 | PARSE_ERROR: Unknown command: rotate (61) |
| `execute if block` | 59 | PARSE_ERROR: Macros are not supported in this location (59) |
| `execute if entity` | 31 | PARSE_ERROR: Failed to parse any options @ execute (29) |
| `data modify storage` | 26 | PARSE_ERROR: Macros are not supported in this location (25) |
| `bossbar` | 23 | PARSE_ERROR: Unknown command: bossbar (23) |
| `execute at` | 21 | PARSE_ERROR: Expected a selector (15) |
| `execute run advancement grant` | 17 | PARSE_ERROR: Missing space after argument @ advancement grant <targets:entity_selector[pla (17) |
| `setblock` | 17 | PARSE_ERROR: Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate (16) |
| `effect` | 17 | PARSE_ERROR: Invalid integer (15) |
| `(whole function)` | 16 | COMPILE_ERROR: `return` inside an unparsed command would be swallowed; falling back the wh (16) |
| `execute facing` | 14 | PARSE_ERROR: Failed to parse any options @ execute (14) |
| `execute as` | 13 | PARSE_ERROR: Failed to parse any options @ execute (9) |
| `place` | 13 | PARSE_ERROR: Unknown command: place (13) |
| `forceload` | 12 | PARSE_ERROR: Unknown command: forceload (12) |
| `damage` | 12 | PARSE_ERROR: Unknown command: damage (12) |
| `execute if score` | 10 | PARSE_ERROR: Failed to parse any options @ execute (10) |
| `scoreboard objectives modify` | 8 | PARSE_ERROR: Failed to parse any options @ scoreboard objectives (8) |
| `function` | 8 | PARSE_ERROR: Macros are not supported in this location (7) |
| `execute positioned` | 8 | PARSE_ERROR: Failed to parse any options @ execute (5) |
| `execute unless data` | 7 | PARSE_ERROR: Macros are not supported in this location (7) |
| `execute store success` | 7 | PARSE_ERROR: Failed to parse any options @ execute (7) |
| `tellraw` | 5 | PARSE_ERROR: Macro commands are not supported for json arguments (5) |
| `data remove storage` | 5 | PARSE_ERROR: Macros are not supported in this location (5) |
| `scoreboard players reset` | 5 | PARSE_ERROR: Too many arguments for @ scoreboard players reset <targets:score_holder> (5) |
| `stopsound` | 5 | PARSE_ERROR: Missing space after argument @ stopsound <targets:entity_selector[players]> < (5) |
| `summon` | 4 | PARSE_ERROR: Macros are not supported in this location (2) |
| `say` | 4 | PARSE_ERROR: Unknown command: say (4) |

## (iii) "Macros are not supported in this location"

140 commands. By command word:

`execute if block` 59, `data modify storage` 25, `execute unless data` 7, `function` 7, `data remove storage` 5, `execute as` 4, `execute run data modify storage` 4, `execute positioned` 3, `scoreboard objectives remove` 2, `scoreboard objectives add` 2, `summon` 2, `execute run function` 2, `execute run return` 2, `execute run data merge entity` 2, `execute rotated` 2

| command | argument position | count |
|---|---|---|
| `execute if block` | arg 4 | 59 |
| `data modify storage` | arg 9 | 14 |
| `function` | arg 2 (inside {...}) | 7 |
| `execute unless data` | arg 3 | 6 |
| `execute as` | arg 1 (inside {...}) | 3 |
| `data modify storage` | arg 7 | 3 |
| `execute positioned` | arg 1 | 3 |
| `data modify storage` | arg 5 (inside {...}) | 3 |
| `data modify storage` | arg 10 (inside {...}) | 3 |
| `data remove storage` | arg 5 (inside {...}) | 3 |
| `data remove storage` | arg 4 | 2 |
| `data modify storage` | arg 4 | 2 |
| `scoreboard objectives remove` | arg 3 | 2 |
| `scoreboard objectives add` | arg 3 | 2 |
| `summon` | arg 5 (inside {...}) | 2 |
| `execute run function` | arg 2 (inside {...}) | 2 |
| `execute run return` | arg 4 (inside {...}) | 2 |
| `execute run data merge entity` | arg 4 (inside {...}) | 2 |
| `execute rotated` | arg 1 | 2 |
| `execute run data modify storage` | arg 16 (inside {...}) | 2 |
| `execute if entity` | arg 1 (inside {...}) | 1 |
| `setblock` | arg 4 | 1 |
| `execute run data get storage` | arg 4 | 1 |
| `data merge storage` | arg 4 (inside {...}) | 1 |
| `tag` | arg 3 | 1 |

## (iv) compile failures (COMPILE_ERROR / whole-function fallback)

| kind | message | count | packs | function ids (first 4) |
|---|---|---|---|---|
| COMPILE_ERROR | `return` inside an unparsed command would be swallowed; falling back the whole function | 16 | mr-veinminer (6), vt-graves (3), mr-tree-vein-miner (2) | `veinminer:block_add`, `veinminer:block_edit`, `veinminer:internal/check/_loopi`, `veinminer:internal/mine/_loopi` |
