# kitchen-sink: OK

- kind: worldgen, packs: bacap, vt-multiplayer-sleep, vt-cauldron-concrete, vt-afk-display, vt-coordinates-hud, vt-durability-ping, vt-track-statistics, vt-track-raw-statistics, vt-armor-statues, vt-fast-leaf-decay, vt-graves, vt-homes, vt-tpa, mr-timber-strike, mr-veinminer, mr-tree-vein-miner, mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 6060/6060 functions compiled (100.0%), partial fallback commands: 297
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 7, low-confidence 75, noise paths 3067; worldgen (gen dump) findings 6
- exceptions (non-ignorable lines): base1=0, base2=2, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 36.01 ms (min 29.9, max 37.51); mcfc median 30.26 ms (min 29.29, max 39.81)
- mcfc debug: 5636 compiled | 2 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9502
- exerciser (mcfc): ran 5944 of 6060 candidate functions (macro skipped 63, name skipped 50, scenario skipped 3), errors 333, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 130
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Unknown command: bossbar: 15
- Expected a selector: 15
- Unknown command: rotate: 11
- Failed to parse any options @ scoreboard objectives: 8
- Macro commands are not supported for json arguments: 5
- Unknown command: forceload: 5
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 5
- Unknown command: ride: 2
- Too many arguments for @ loot spawn <pos:vec3> loot <loot_table:resource_location>: 2
- Invalid integer: 2
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Expected '{' while reading nbt compound, but got '$': 1

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-2,-7`
- [gen] `blocks[overworld].columns.-1,-3`
- [gen] `blocks[overworld].columns.-1,2`
- [gen] `blocks[overworld].columns.-2,-7`
- [gen] `blocks[overworld].columns.-2,1`
- [gen] `blocks[overworld].columns.-3,0`
- [gen] `blocks[overworld].columns.0,3`

## low-confidence findings (75, first 10; not counted)
- `blocks[overworld].columns.-1,-3`
- `blocks[overworld].columns.-1,2`
- `blocks[overworld].columns.-2,1`
- `blocks[overworld].columns.-3,0`
- `blocks[overworld].columns.0,3`
- `entities.minecraft:overworld.list[minecraft:item#122].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#138].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#15].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#198].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#199].pos[0]`

## exceptions base2
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester2 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
