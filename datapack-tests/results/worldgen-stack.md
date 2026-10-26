# worldgen-stack: DIFF

- kind: worldgen, packs: mr-terralith, mr-incendium, mr-nullscape, mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 2324/2327 functions compiled (99.9%), partial fallback commands: 273
- whole-function fallbacks: 0, compile failures: 3, runtime disables: 0
- findings: confirmed 6, flaky 32, low-confidence 4, noise paths 582; worldgen (gen dump) findings 7
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.87 ms (min 1.65, max 2.29); mcfc median 1.55 ms (min 1.38, max 1.77)
- mcfc debug: 1940 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 2307 of 2327 candidate functions (macro skipped 5, name skipped 15, scenario skipped 0), errors 392, slow 0

## top partial-fallback reasons
- Failed to parse any options @ execute: 90
- Unknown command: attribute: 56
- Unknown command: rotate: 50
- Invalid integer: 15
- Unknown command: place: 13
- Unknown command: damage: 11
- Unknown command: bossbar: 8
- Unknown command: forceload: 7
- Missing space after argument @ stopsound <targets:entity_selector[players]> <source:word>: 7
- Macros are not supported in this location: 5
- Unknown command: spreadplayers: 4
- Failed to parse any options @ execute store <store_location:result_store_location_type>: 2
- Mixed positive and negated type options are resolved by vanilla: 1
- Too many arguments for @ fill <from:block_pos> <to:block_pos> <block:block_predicate> replace <filter:block_predicate>: 1
- Missing space after argument @ summon <entity:resource_location>: 1

## confirmed findings (first 20)
- [final] `scoreboard.teams.in.castle.members[20]`: base="<U?>" | mcfc="DPTester1"
- [final] `scoreboard.teams.in.sanctum.members[2]`: base=<missing> | mcfc="<U?>"
- [final] `scoreboard.teams.in.sanctum.members[3]`: base=<missing> | mcfc="<U?>"
- [gen] `blocks[overworld].columns.-6,-1`: base="396910a29cc797a3336313867aa9f11d342b30698a0bac5f490b41227b6857ea" | mcfc="c5ad11b66b79237824e955aa263ea0de489296cbc156c2c0bce15d22daac03bc"
- [gen] `blocks[overworld].columns.-6,0`: base="aa0f2c93d425e7af708748425af5b11a755ba1f3937b8b295b249e853f3eab22" | mcfc="f4833cdce71ca9bc68544191bea15dc0854fcd92f6fb598fb5755b423c6cf774"
- [gen] `blocks[overworld].columns.5,-5`: base="154eed7e2ef3e6471b4edafe3eaf574b1f5a35d8e6ed322e336d55d8a96cbe0d" | mcfc="8c127a338f05ce05d0eb795b442ab4414561771013bfa3f57ff71249743b24c0"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.-2,-7`
- [final] `blocks[overworld].columns.4,-5`
- [final] `blocks[overworld].columns.7,-1`
- [final] `blocks[overworld].columns.7,-2`
- [final] `entities.minecraft:overworld.list[minecraft:cat#1].tags[3]`
- [final] `entities.minecraft:overworld.list[minecraft:cat#1].tags[4]`
- [final] `entities.minecraft:overworld.list[minecraft:cat#2].tags[3]`
- [final] `entities.minecraft:overworld.list[minecraft:cat#3].tags[2]`
- [final] `entities.minecraft:overworld.list[minecraft:iron_golem#1].tags[1]`
- [final] `entities.minecraft:overworld.list[minecraft:pig#3].tags[1]`

## low-confidence findings (4, first 10; not counted)
- `blocks[overworld].columns.-5,-3`
- `blocks[overworld].columns.-6,-1`
- `blocks[overworld].columns.-6,0`
- `blocks[overworld].columns.5,-5`
