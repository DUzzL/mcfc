# mr-incendium-tp-noex: DIFF

- kind: mixed, packs: mr-incendium
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 2312/2314 functions compiled (99.9%), partial fallback commands: 255
- whole-function fallbacks: 0, compile failures: 2, runtime disables: 0
- findings: confirmed 9, flaky 5, low-confidence 6, noise paths 627; worldgen (gen dump) findings 7
- exceptions (non-ignorable lines): base1=2, base2=0, mcfc=0
- mcfc debug: 14 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0

## top partial-fallback reasons
- Failed to parse any options @ execute: 89
- Unknown command: attribute: 56
- Unknown command: rotate: 50
- Invalid integer: 15
- Unknown command: damage: 11
- Unknown command: bossbar: 8
- Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word>: 7
- Unknown command: forceload: 5
- Macros are not supported in this location: 4
- Unknown command: spreadplayers: 3
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Mixed positive and negated type options are resolved by vanilla: 1
- Missing space after argument @ summon <entity:resource_location>: 1
- Only one entity is allowed, but the provided selector allows more than one: 1
- Combined type options are resolved by vanilla: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.7,3`: base="a2402a34874c8bc6c68c976b72f37c3d5eb17589fed86e52e9d7760abfd80002" | mcfc="fb4bf9c37dc11db158247b07eda9d624c643505d0235b9c1bc3823c5e6b2b372"
- [final] `entities.minecraft:overworld.list[minecraft:bee#12].tags[2]`: base="in.eid_5" | mcfc="in.eid_2"
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#6].tags[1]`: base="in.activate_mid" | mcfc="in.activate_near"
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#6].tags[2]`: base="in.castle" | mcfc="in.alchemist"
- [final] `entities.minecraft:the_nether.list[minecraft:piglin_brute#6].tags[3]`: base="in.checked" | mcfc="in.castle"
- [gen] `blocks[overworld].columns.0,-5`: base="f9eed68ecd1fcbb0e86004b536497189318a9c83b8c069dfe853573c7af50bcb" | mcfc="d4db5b464725fbea7aa746a1137f2c76673114599bf6a208e8ff25888448052b"
- [gen] `blocks[overworld].columns.0,-6`: base="d1ced48ad4fb737912b886a75e621626d77552bea72b1252e63ee02300ee0fe3" | mcfc="bc81f6bf048810b60f02bc6c5abe3c442df794c1b76578dcc2121d48ee9a6435"
- [gen] `blocks[overworld].columns.2,-2`: base="1e6853f47a3b6186ddc067306b84256ce675b64063935f8e9e851b2653f098c4" | mcfc="b4eab3f27fc934c0e49083809815a6ef2cf714ed638be7766b2bfeb482b6b13c"
- [gen] `blocks[overworld].columns.7,3`: base="a2402a34874c8bc6c68c976b72f37c3d5eb17589fed86e52e9d7760abfd80002" | mcfc="fb4bf9c37dc11db158247b07eda9d624c643505d0235b9c1bc3823c5e6b2b372"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.7,-1`
- [final] `entities.minecraft:overworld.list[minecraft:bee#12].tags[3]`
- [gen] `blocks[overworld].columns.-3,-1`
- [gen] `blocks[overworld].columns.-6,5`
- [gen] `blocks[overworld].columns.7,-1`

## low-confidence findings (6, first 10; not counted)
- `blocks[overworld].columns.-3,-1`
- `blocks[overworld].columns.-6,5`
- `blocks[overworld].columns.0,-5`
- `blocks[overworld].columns.2,-2`
- `blocks[overworld].columns.6,-8`
- `entities.minecraft:overworld.list[minecraft:item#3].nbt`

## exceptions base1
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester2 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
