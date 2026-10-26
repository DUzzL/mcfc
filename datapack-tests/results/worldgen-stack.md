# worldgen-stack: DIFF

- kind: worldgen, packs: mr-terralith, mr-incendium, mr-nullscape, mr-structory
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-bfaa7efd.jar sha256=fca3ca87409825f94361fa4e189433dcced4f759e339f2b7bc1999eb69d81ed0 (file mtime 2026-10-26 13:45:38)
- compile: 2324/2327 functions compiled (99.9%), partial fallback commands: 273
- whole-function fallbacks: 0, compile failures: 3, runtime disables: 0
- findings: confirmed 4, flaky 36, low-confidence 3, noise paths 569; worldgen (gen dump) findings 8
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 1.73 ms (min 1.43, max 2.38); mcfc median 1.86 ms (min 1.56, max 2.05)
- mcfc debug: 1942 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
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
- [final] `scoreboard.scores.$toggle.in.constants`: base=1 | mcfc=0
- [final] `scoreboard.teams.in.noname.members[2]`: base=<missing> | mcfc="<U?>"
- [final] `scoreboard.teams.in.noname.members[3]`: base=<missing> | mcfc="<U?>"
- [final] `scoreboard.teams.in.noname.members[4]`: base=<missing> | mcfc="<U?>"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[nether].columns.0,2`
- [final] `blocks[nether].columns.2,-2`
- [final] `blocks[nether].columns.2,-3`
- [final] `blocks[overworld].columns.0,-6`
- [final] `blocks[overworld].columns.6,0`
- [final] `blocks[overworld].columns.6,1`
- [final] `entities.minecraft:overworld.list[minecraft:pig#5].tags[1]`
- [final] `entities.minecraft:overworld.list[minecraft:pig#5].tags[2]`
- [final] `entities.minecraft:overworld.list[minecraft:pig#5].tags[3]`
- [final] `entities.minecraft:overworld.list[minecraft:pig#5].tags[4]`

## low-confidence findings (3, first 10; not counted)
- `blocks[overworld].columns.-8,3`
- `blocks[overworld].columns.5,-1`
- `entities.minecraft:overworld.list[minecraft:item#9].nbt`
