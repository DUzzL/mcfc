# bacap: DIFF

- kind: function, packs: bacap
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 5435/5436 functions compiled (100.0%), partial fallback commands: 98
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 51, flaky 43, low-confidence 0, noise paths 3323
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 30.43 ms (min 30.09, max 31.1); mcfc median 29.97 ms (min 29.23, max 30.05)
- mcfc debug: 5423 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8284
- exerciser (mcfc): ran 5418 of 5436 candidate functions (macro skipped 8, name skipped 10, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Failed to parse any options @ scoreboard objectives: 8

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#1179].pos[0]`: base=0.67 | mcfc=0.68
- [final] `entities.minecraft:overworld.list[minecraft:item#1214].customName`: base=null | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1214].pos[1]`: base=-60.0 | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1214].tags[]`: base=[] | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1214].type`: base="minecraft:item" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1214].uuid`: base="<item#1214>" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1215].customName`: base=null | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1215].pos[1]`: base=-60.0 | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1215].tags[]`: base=[] | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1215].type`: base="minecraft:item" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1215].uuid`: base="<item#1215>" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1216].customName`: base=null | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1216].pos[1]`: base=-60.0 | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1216].tags[]`: base=[] | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1216].type`: base="minecraft:item" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1216].uuid`: base="<item#1216>" | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1217].customName`: base=null | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1217].pos[1]`: base=-60.0 | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1217].tags[]`: base=[] | mcfc=<missing>
- [final] `entities.minecraft:overworld.list[minecraft:item#1217].type`: base="minecraft:item" | mcfc=<missing>

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:item#1000].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1001].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1002].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1003].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1080].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1081].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1082].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1083].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1084].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:item#1138].pos[0]`
