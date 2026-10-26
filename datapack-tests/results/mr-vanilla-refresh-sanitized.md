# mr-vanilla-refresh-sanitized: DIFF

- kind: function, packs: mr-vanilla-refresh-sanitized
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 982/1011 functions compiled (97.1%), partial fallback commands: 223
- whole-function fallbacks: 0, compile failures: 29, runtime disables: 0
- findings: confirmed 1, flaky 5, low-confidence 0, noise paths 58
- exceptions (non-ignorable lines): base1=110, base2=110, mcfc=110
- mcfc debug: 35 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 859 of 1011 candidate functions (macro skipped 62, name skipped 90, scenario skipped 0), errors 832, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 84
- Failed to parse any options @ scoreboard objectives: 46
- Macro commands are not supported for json arguments: 38
- Unsupported entity type: minecraft:: 20
- Missing space after argument @ function <functions:function>: 11
- Invalid double: 7
- Failed to parse any options @ execute: 4
- Unknown command: attribute: 3
- Missing space after argument @ playsound <sound:resource_location>: 3
- Unknown command: damage: 2
- Mixed positive and negated type options are resolved by vanilla: 2
- Missing space after argument @ execute in <dimension:resource_location>: 1
- Missing space after argument @ summon <entity:resource_location>: 1
- Invalid difficulty: : 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#4].nbt`: base="{Age:317s,Health:5s,Invulnerable:0b,Item:{count:2,id:\"minecraft:beef\"},PickupDelay:0s}" | mcfc="{Age:317s,Health:5s,Invulnerable:0b,Item:{count:2,id:\"minecraft:leather\"},PickupDelay:0s}"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#11].customName`
- [final] `entities.minecraft:overworld.list[minecraft:item#11].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#11].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:item#11].type`
- [final] `entities.minecraft:overworld.list[minecraft:item#11].uuid`

## exceptions base1
- java.util.concurrent.CompletionException x55: java.lang.IllegalArgumentException: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_perce
- java.lang.IllegalArgumentException x55: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_percent' in registry 'minecraft:predicate

## exceptions base2
- java.util.concurrent.CompletionException x55: java.lang.IllegalArgumentException: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_perce
- java.lang.IllegalArgumentException x55: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_percent' in registry 'minecraft:predicate

## exceptions mcfc
- java.util.concurrent.CompletionException x55: java.lang.IllegalArgumentException: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_perce
- java.lang.IllegalArgumentException x55: Whilst parsing command on line 6: Can't find element 'vanilla_refresh:chance/5_percent' in registry 'minecraft:predicate

## Investigate
- Only finding is `entities...item#4.nbt` beef vs leather (cow loot after `damage @s 100`): item order/physics noise, not counted. Perf not measured (`--no-perf`).
- Low coverage: 55 functions fail to parse in all variants (`Can't find element 'vanilla_refresh:chance/5_percent' in registry 'minecraft:predicate'`), because the predicates were removed to make the pack load.
- 29 compile failures, all "`return` inside an unparsed command would be swallowed; falling back the whole function" (examples `vanilla_refresh:wand/z/display/text/display`, `vanilla_refresh:other/actions/teleport_to_last_death`).
