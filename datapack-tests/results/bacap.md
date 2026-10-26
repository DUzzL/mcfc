# bacap: OK

- kind: function, packs: bacap
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 5436/5436 functions compiled (100.0%), partial fallback commands: 98
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 37, noise paths 3541; not re-run (no findings)
- exceptions (non-ignorable lines): base1=2, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 32.97 ms (min 32.78, max 33.84); mcfc median 33.15 ms (min 32.96, max 33.78)
- mcfc debug: 5423 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 5418 of 5436 candidate functions (macro skipped 8, name skipped 10, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Failed to parse any options @ scoreboard objectives: 8

## low-confidence findings (37, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#1198].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#1204].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#1205].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#1206].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#131].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#132].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#133].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#134].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#153].pos[0]`
- `entities.minecraft:overworld.list[minecraft:item#154].pos[0]`

## exceptions base1
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester1 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
