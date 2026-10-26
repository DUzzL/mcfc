# mr-dungeons-and-taverns: DIFF

- kind: worldgen, packs: mr-dungeons-and-taverns
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 65/65 functions compiled (100.0%), partial fallback commands: 213
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 10, flaky 25, low-confidence 16, noise paths 359; worldgen (gen dump) findings 14
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.57 ms (min 0.45, max 0.7); mcfc median 0.47 ms (min 0.42, max 0.83)
- mcfc debug: 4 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 65 of 65 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 65, slow 0

## top partial-fallback reasons
- Unknown command: tell: 204
- Unknown command: attribute: 5
- Unknown command: ride: 2
- Cannot turn "{"atlas":"minecraft:items","sprite":"dnt:item/16x_logo"}" into Text: 1
- Unknown command: damage: 1

## confirmed findings (first 20)
- [final] `blocks[overworld].columns.-5,0`: base="2a2408118486e6b7e42941eaa92272e3701ba1d19863cc34d812efb9ae412508" | mcfc="c2a783568af0f5cc4b6c41bfe85d19a6226794f994de96ef3193345d95a56514"
- [final] `blocks[overworld].columns.7,-1`: base="1d74cf44d0c0c5007a9549c44288aab1c3ec794f5f1583abd0eefbcdb6e502db" | mcfc="e33c07bc247c7638935fbfca33fc8d9929499f0e62af5e1e5216b0abdc315f8c"
- [final] `world.dayTime`: base=608 | mcfc=610
- [final] `world.gameTime`: base=608 | mcfc=610
- [gen] `blocks[overworld].columns.-4,0`: base="8ab440db508bee84a2ac91d0df6af5ff98145d1c71fb45860e86b5c9ca967945" | mcfc="00c7478c8194b7ed653cc013db5711b9823e4ccea308d10bdc1d343e9811f678"
- [gen] `blocks[overworld].columns.-5,-1`: base="b9a838e75c3254acff25b6edae8d2927af98e5a147fd886cd81730a4d67583e6" | mcfc="f8815410f785d382e690dd322829f4043b68e7c3f50444a5aeb3bff0ed561dd9"
- [gen] `blocks[overworld].columns.-5,0`: base="2a2408118486e6b7e42941eaa92272e3701ba1d19863cc34d812efb9ae412508" | mcfc="c2a783568af0f5cc4b6c41bfe85d19a6226794f994de96ef3193345d95a56514"
- [gen] `blocks[overworld].columns.6,-1`: base="a32845d027c1ee411c7ad6be7222435393e977287c6a4bb370e23588f431ea4f" | mcfc="3cc30b28201bd3aecbb7b0473467c19e98574e58cd89b74a877712b345bed076"
- [gen] `blocks[overworld].columns.7,-1`: base="1d74cf44d0c0c5007a9549c44288aab1c3ec794f5f1583abd0eefbcdb6e502db" | mcfc="e33c07bc247c7638935fbfca33fc8d9929499f0e62af5e1e5216b0abdc315f8c"
- [gen] `blocks[overworld].columns.7,0`: base="cde0a92bf720a4b91ebd3a23942763ebb1c45a4ad45e930853e120e3b8471d22" | mcfc="f3215f87902c2104dcf6f42022e1972d42449d251c3f209124835207c82a314b"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.7,-3`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].customName`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].type`
- [final] `entities.minecraft:overworld.list[minecraft:bee#15].uuid`
- [final] `entities.minecraft:overworld.list[minecraft:bee#16].customName`

## low-confidence findings (16, first 10; not counted)
- `blocks[overworld].columns.-1,4`
- `blocks[overworld].columns.-1,5`
- `blocks[overworld].columns.-4,0`
- `blocks[overworld].columns.-5,-1`
- `blocks[overworld].columns.1,-4`
- `blocks[overworld].columns.2,-4`
- `blocks[overworld].columns.2,-5`
- `blocks[overworld].columns.5,7`
- `blocks[overworld].columns.6,-1`
- `blocks[overworld].columns.7,-6`
