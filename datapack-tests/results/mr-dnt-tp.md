# mr-dnt-tp: DIFF

- kind: mixed, packs: mr-dungeons-and-taverns
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 65/65 functions compiled (100.0%), partial fallback commands: 238
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 10, flaky 30, low-confidence 12, noise paths 503; worldgen (gen dump) findings 20
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- mcfc debug: 10 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 65 of 65 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 65, slow 0

## top partial-fallback reasons
- Missing space after argument @ execute if predicate <predicate:resource_location>: 226
- Unknown command: attribute: 5
- Missing space after argument @ execute unless predicate <predicate:resource_location>: 3
- Unknown command: ride: 2
- Cannot turn "{"atlas":"minecraft:items","sprite":"dnt:item/16x_logo"}" into Text: 1
- Unknown command: damage: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.2,2`: base="202f9a838a4ee32d131466b67f2f9c697e531ad6f809ddd46e539e4f93f289e2" | mcfc="ab94ec307a86ea6728018361d0e8e3926a7da8007239749c657eeff34ed6a1f6"
- [final] `blocks[overworld].columns.7,-1`: base="1d74cf44d0c0c5007a9549c44288aab1c3ec794f5f1583abd0eefbcdb6e502db" | mcfc="e33c07bc247c7638935fbfca33fc8d9929499f0e62af5e1e5216b0abdc315f8c"
- [final] `entities.minecraft:overworld.list[minecraft:pillager#6].pos[1]`: base=67.0 | mcfc=66.0
- [gen] `blocks[overworld].columns.-8,-4`: base="2c8f31e802e9dd3a4e49c060f9478038f772e411a1f56228dc48bdd6361b7ec4" | mcfc="48c96207be0ddd0d35a392b6ab8683d7fc46b07d641e11555e68eea8cd263376"
- [gen] `blocks[overworld].columns.2,2`: base="202f9a838a4ee32d131466b67f2f9c697e531ad6f809ddd46e539e4f93f289e2" | mcfc="ab94ec307a86ea6728018361d0e8e3926a7da8007239749c657eeff34ed6a1f6"
- [gen] `blocks[overworld].columns.2,3`: base="24b152e827ab38ee2b647b0a1102bbd58c221ad3d434c07fd0bbc8fbb04d9d29" | mcfc="288ded9eef4d519e00f866f5231e0c7b9cc032253cbbf48a9903682a33bc40d8"
- [gen] `blocks[overworld].columns.3,2`: base="a72622f6c69598e405ed4d9a4974a1a4aacfc3c6a01d6f7ecc125f365c612f78" | mcfc="2548ea6fc1a897e376e479e75cc9b3c0c574ccced30dc4ff89c965bdce57c0a7"
- [gen] `blocks[overworld].columns.6,-1`: base="a32845d027c1ee411c7ad6be7222435393e977287c6a4bb370e23588f431ea4f" | mcfc="3cc30b28201bd3aecbb7b0473467c19e98574e58cd89b74a877712b345bed076"
- [gen] `blocks[overworld].columns.7,-1`: base="1d74cf44d0c0c5007a9549c44288aab1c3ec794f5f1583abd0eefbcdb6e502db" | mcfc="e33c07bc247c7638935fbfca33fc8d9929499f0e62af5e1e5216b0abdc315f8c"
- [gen] `blocks[overworld].columns.7,-8`: base="870023c0fb9cd94ee0c0bda1b0dc59d4c14a040f93c524dd0c801251d06cbee3" | mcfc="bf027248610538ce238d0c4319efec1aa522bcfcb25a79c631e0fbeb6c4c832c"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[nether].columns.-3,-2`
- [final] `blocks[nether].columns.-3,-3`
- [final] `blocks[nether].columns.-4,-4`
- [final] `blocks[overworld].columns.-8,-8`
- [final] `blocks[overworld].columns.-8,3`
- [final] `blocks[overworld].columns.-8,4`
- [final] `blocks[overworld].columns.0,1`
- [final] `entities.minecraft:overworld.counts.minecraft:fireball`
- [final] `entities.minecraft:overworld.list[minecraft:fireball#1].customName`
- [final] `entities.minecraft:overworld.list[minecraft:fireball#1].nbt`

## low-confidence findings (12, first 10; not counted)
- `blocks[overworld].columns.-1,1`
- `blocks[overworld].columns.-3,-2`
- `blocks[overworld].columns.-3,-3`
- `blocks[overworld].columns.-8,-4`
- `blocks[overworld].columns.0,0`
- `blocks[overworld].columns.2,3`
- `blocks[overworld].columns.3,2`
- `blocks[overworld].columns.6,-1`
- `blocks[overworld].columns.7,-8`
- `blocks[overworld].columns.7,0`
