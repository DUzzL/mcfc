# mr-guns_plus_plus-norecipe: DIFF

- kind: function, packs: mr-guns_plus_plus-norecipe
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 459/459 functions compiled (100.0%), partial fallback commands: 203
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 5, flaky 8, low-confidence 0, noise paths 148
- exceptions (non-ignorable lines): base1=170, base2=170, mcfc=170
- MSPT (solo, sprint 1200 x3): vanilla median 2.99 ms (min 2.95, max 3.22); mcfc median 2.87 ms (min 2.81, max 3.2)
- mcfc debug: 59 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 397 of 459 candidate functions (macro skipped 56, name skipped 6, scenario skipped 0), errors 347, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 173
- Mixed positive and negated type options are resolved by vanilla: 8
- Unknown command: attribute: 4
- Missing space after argument @ execute if predicate <predicate:resource_location>: 4
- Missing space after argument @ execute unless predicate <predicate:resource_location>: 3
- Unknown command: forceload: 2
- Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word>: 2
- Failed to parse any options @ execute: 2
- Failed to parse any options @ tag <targets:entity_selector>: 1
- Unknown command: damage: 1
- Failed to parse any options @ data modify <destination:nbt_data_location> <path:nbt_path> set: 1
- Failed to parse any options @ setblock <pos:block_pos> <block:block_predicate>: 1
- Unknown command: dialog: 1

## confirmed findings (first 20)
- [final] `scoreboard.scores.DPTester1.bloomRandom`: base=12 | mcfc=16
- [final] `scoreboard.scores.DPTester1.size_time`: base=<missing> | mcfc=1000
- [final] `scoreboard.scores.DPTester2.bloomRandom`: base=20 | mcfc=2
- [final] `scoreboard.scores.DPTester2.shotcounterdelay`: base=<missing> | mcfc=-1
- [final] `scoreboard.scores.DPTester2.size_time`: base=<missing> | mcfc=1000

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#31].customName`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].type`
- [final] `entities.minecraft:overworld.list[minecraft:item#31].uuid`

## exceptions base1
- java.util.concurrent.CompletionException x85: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Fai
- java.lang.IllegalArgumentException x85: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Failed to get element thepa:left_click_

## exceptions base2
- java.util.concurrent.CompletionException x85: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Fai
- java.lang.IllegalArgumentException x85: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Failed to get element thepa:left_click_

## exceptions mcfc
- java.util.concurrent.CompletionException x85: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Fai
- java.lang.IllegalArgumentException x85: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Failed to get element thepa:left_click_

## exceptions perfbase
- java.util.concurrent.CompletionException x85: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Fai
- java.lang.IllegalArgumentException x85: Whilst parsing command on line 1: Malformed 'minecraft:enchantments' component: 'Failed to get element thepa:left_click_

## Investigate
- Scenario gun items are still not created in any variant (`give` functions fail to parse: 85 functions with `enchantments={"thepa:left_click_detect":1}` never load in vanilla either); scenario effectively only exercises the tick loop. Equal in all variants (170 exceptions each).
- Confirmed (both attempts) without any scenario/exerciser (repro scenarios `dynscale`, `resetall`): `scoreboard.scores.DPTester1.size_time` base=<missing> mcfc=1000, `...shotcounterdelay` base=<missing> mcfc=-1, `...bloomRandom` base=12 mcfc=16 (DPTester2: 20 vs 2).
- Root cause (minimal repro `resetall`, zip `cache\packs\resetall.zip`): `scoreboard players reset <targets>` WITHOUT objective is a no-op in mcfc. Tested forms, all ignored by mcfc, all work in vanilla: `scoreboard players reset @a[name=DPTester1]`, `scoreboard players reset @e[scores={a=1},name=DPTester2]`, `scoreboard players reset #x1`, `scoreboard players reset *`. With objective (`reset #x2 a`) works. Dump: base1 `{#init:{a:1},#x2:{b:2},#x4:{a:1}}`, mcfc additionally keeps `#x1`, `DPTester1`, `DPTester2` with a=1,b=2.
- Pack line: `thepa:utilities/dynamic_scale` line 3 `scoreboard players reset @e[scores={size_time=1000}]` (called from `thepa:loop` line 111). Per-tick trace (`dynscale` scenario, `dump` after every `tick step 1`): base1 DPTester1 t1 = none, t2.. `{other:n}`; mcfc every tick `{other:n+1,size_time:1000}`.
- Item entity pos[1] diffs (-60 vs -62): physics noise.
