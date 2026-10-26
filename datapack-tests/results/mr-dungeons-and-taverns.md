# mr-dungeons-and-taverns: DIFF

- kind: worldgen, packs: mr-dungeons-and-taverns
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 65/65 functions compiled (100.0%), partial fallback commands: 213
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 5, flaky 27, low-confidence 7, noise paths 356; worldgen (gen dump) findings 11
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.68 ms (min 0.52, max 0.93); mcfc median 0.69 ms (min 0.55, max 1.01)
- mcfc debug: 4 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 65 of 65 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 65, slow 0

## top partial-fallback reasons
- Unknown command: tell: 204
- Unknown command: attribute: 5
- Unknown command: ride: 2
- Cannot turn "{"atlas":"minecraft:items","sprite":"dnt:item/16x_logo"}" into Text: 1
- Unknown command: damage: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.-8,0`: base="64ccbe3a07c4a6ce9a19b790248c9339fce3568f36039e4aac2b92c2fde1e833" | mcfc="1f31bf18cc05da8d0fa9512e012fefb748c93911ff538cd4d4bac499b0132ae1"
- [final] `entities.minecraft:overworld.counts.minecraft:bee`: base=14 | mcfc=16
- [gen] `blocks[overworld].columns.-8,0`: base="64ccbe3a07c4a6ce9a19b790248c9339fce3568f36039e4aac2b92c2fde1e833" | mcfc="1f31bf18cc05da8d0fa9512e012fefb748c93911ff538cd4d4bac499b0132ae1"
- [gen] `blocks[overworld].columns.2,-4`: base="068768a59d292d7a36625292fd4115c05aa42adc9c200597f69737a3a2d479a0" | mcfc="1360642165c4d0a36ef41a0e9cdafeea63ffba6c42be1b96be9ff2df69cfff0d"
- [gen] `blocks[overworld].columns.2,-5`: base="fcd68a9f5c42f540b4028a9dc37b111ac3af0688dab0bea691c3a54d8bd7c54d" | mcfc="af4c08394721583bcafb60c133942cbdb99d92041be025e3d0b9aee68c2ba978"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-7,1`
- [final] `blocks[overworld].columns.1,3`
- [final] `blocks[overworld].columns.4,3`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].customName`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].type`

## low-confidence findings (7, first 10; not counted)
- `blocks[overworld].columns.-1,4`
- `blocks[overworld].columns.-1,5`
- `blocks[overworld].columns.-7,0`
- `blocks[overworld].columns.2,-4`
- `blocks[overworld].columns.2,-5`
- `blocks[overworld].columns.4,2`
- `blocks[overworld].columns.5,7`

## exceptions perfbase
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/3c60c0c23d05399fbffb864ec993da33?unsigned
- java.net.SocketTimeoutException x1: Read timed out
