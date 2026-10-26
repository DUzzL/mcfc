# kitchen-sink: DIFF

- kind: worldgen, packs: bacap, vt-multiplayer-sleep, vt-cauldron-concrete, vt-afk-display, vt-coordinates-hud, vt-durability-ping, vt-track-statistics, vt-track-raw-statistics, vt-armor-statues, vt-fast-leaf-decay, vt-graves, vt-homes, vt-tpa, mr-timber-strike, mr-veinminer, mr-tree-vein-miner, mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 6048/6060 functions compiled (99.8%), partial fallback commands: 297
- whole-function fallbacks: 0, compile failures: 12, runtime disables: 0
- findings: confirmed 2, flaky 10, low-confidence 28, noise paths 3102; worldgen (gen dump) findings 8
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=2
- MSPT (solo, sprint 1200 x3): vanilla median 24.12 ms (min 23.45, max 25.11); mcfc median 23.19 ms (min 22.57, max 24.17)
- mcfc debug: 5637 compiled | 3 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8419
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

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.4,1`: base="53add80c94a2e804ecad8dc37d74712da31b39d893afbe22fdc516f72a5f8cdb" | mcfc="73f5271e1c1d665ba9e3464713ff5836c28af73faea1273377d1ec6eebf8f42e"
- [gen] `blocks[overworld].columns.4,1`: base="53add80c94a2e804ecad8dc37d74712da31b39d893afbe22fdc516f72a5f8cdb" | mcfc="73f5271e1c1d665ba9e3464713ff5836c28af73faea1273377d1ec6eebf8f42e"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.3,-2`
- [final] `blocks[overworld].columns.3,-3`
- [final] `blocks[overworld].columns.4,-2`
- [gen] `blocks[overworld].columns.-1,7`
- [gen] `blocks[overworld].columns.-5,-3`
- [gen] `blocks[overworld].columns.3,-2`
- [gen] `blocks[overworld].columns.3,-3`
- [gen] `blocks[overworld].columns.3,7`
- [gen] `blocks[overworld].columns.4,-2`
- [gen] `blocks[overworld].columns.4,2`

## low-confidence findings (28, first 10; not counted)
- `blocks[overworld].columns.3,7`
- `blocks[overworld].columns.4,2`
- `entities.minecraft:overworld.list[minecraft:item#144].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#145].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#205].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#206].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#207].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#208].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#20].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#263].pos[0]`

## exceptions mcfc
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester1 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
