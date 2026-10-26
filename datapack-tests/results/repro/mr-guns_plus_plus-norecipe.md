# mr-guns_plus_plus-norecipe: DIFF

- kind: function, packs: mr-guns_plus_plus-norecipe
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 459/459 functions compiled (100.0%), partial fallback commands: 203
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 1, flaky 0, low-confidence 0, noise paths 164
- exceptions (non-ignorable lines): base1=170, base2=170, mcfc=170
- MSPT (solo, sprint 1200 x3): vanilla median 3.68 ms (min 3.67, max 4.91); mcfc median 4.41 ms (min 3.48, max 4.62)
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
- [final] `entities.minecraft:overworld.list[minecraft:item#25].pos[1]`: base=-60.0 | mcfc=-62.0

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
