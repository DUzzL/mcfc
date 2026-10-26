# bacap-grantall: DIFF

- kind: function, packs: bacap
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 05:45:08)
- compile: 5435/5436 functions compiled (100.0%), partial fallback commands: 98
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 401, flaky 0, low-confidence 0, noise paths 1472; confirmation disabled
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- mcfc debug: 5247 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8471
- exerciser (mcfc): ran 0 of 5436 candidate functions (macro skipped 8, name skipped 10, scenario skipped 5418), errors 0, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Failed to parse any options @ scoreboard objectives: 8

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#10].pos[0]`: base=0.3 | mcfc=0.32
- [final] `entities.minecraft:overworld.list[minecraft:item#12].pos[0]`: base=0.31 | mcfc=0.33
- [final] `entities.minecraft:overworld.list[minecraft:item#13].pos[0]`: base=0.31 | mcfc=0.33
- [final] `entities.minecraft:overworld.list[minecraft:item#166].pos[0]`: base=0.45 | mcfc=0.46
- [final] `entities.minecraft:overworld.list[minecraft:item#167].pos[0]`: base=0.45 | mcfc=0.46
- [final] `entities.minecraft:overworld.list[minecraft:item#168].pos[0]`: base=0.45 | mcfc=0.46
- [final] `entities.minecraft:overworld.list[minecraft:item#194].pos[0]`: base=0.47 | mcfc=0.46
- [final] `entities.minecraft:overworld.list[minecraft:item#216].pos[0]`: base=0.48 | mcfc=0.47
- [final] `entities.minecraft:overworld.list[minecraft:item#217].pos[0]`: base=0.48 | mcfc=0.47
- [final] `entities.minecraft:overworld.list[minecraft:item#218].pos[0]`: base=0.48 | mcfc=0.47
- [final] `entities.minecraft:overworld.list[minecraft:item#243].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#244].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#245].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#246].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#247].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#248].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#249].pos[0]`: base=0.49 | mcfc=0.48
- [final] `entities.minecraft:overworld.list[minecraft:item#29].pos[0]`: base=0.35 | mcfc=0.34
- [final] `entities.minecraft:overworld.list[minecraft:item#30].pos[0]`: base=0.35 | mcfc=0.34
- [final] `entities.minecraft:overworld.list[minecraft:item#31].pos[0]`: base=0.35 | mcfc=0.34
