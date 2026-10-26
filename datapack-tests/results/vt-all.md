# vt-all: FALLBACK-ISSUE

- kind: function, packs: vt-multiplayer-sleep, vt-cauldron-concrete, vt-afk-display, vt-coordinates-hud, vt-durability-ping, vt-track-statistics, vt-track-raw-statistics, vt-armor-statues, vt-fast-leaf-decay, vt-graves, vt-homes, vt-tpa
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 464/467 functions compiled (99.4%), partial fallback commands: 73
- whole-function fallbacks: 0, compile failures: 3, runtime disables: 0
- findings: confirmed 0, flaky 1, low-confidence 0, noise paths 23
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=2
- MSPT (solo, sprint 1200 x3): vanilla median 0.57 ms (min 0.46, max 0.9); mcfc median 0.58 ms (min 0.41, max 0.7)
- mcfc debug: 211 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 414 of 467 candidate functions (macro skipped 18, name skipped 32, scenario skipped 3), errors 221, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 30
- Unknown command: bossbar: 15
- Unknown command: rotate: 11
- Unknown command: forceload: 5
- Unknown command: ride: 2
- Too many arguments for @ loot spawn <pos:vec3> loot <loot_table:resource_location>: 2
- Invalid integer: 2
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Macro commands are not supported for json arguments: 1
- Expected '{' while reading nbt compound, but got '$': 1
- Only one entity is allowed, but the provided selector allows more than one: 1
- Failed to parse any options @ execute: 1

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `scoreboard.scores.#random_type.as_angle`

## exceptions mcfc
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/3c60c0c23d05399fbffb864ec993da33?unsigned
- java.net.SocketTimeoutException x1: Read timed out
