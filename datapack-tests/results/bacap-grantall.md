# bacap-grantall: DIFF

- kind: function, packs: bacap
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 5435/5436 functions compiled (100.0%), partial fallback commands: 98
- whole-function fallbacks: 0, compile failures: 1, runtime disables: 0
- findings: confirmed 187, flaky 0, low-confidence 0, noise paths 1418; confirmation disabled
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- mcfc debug: 5247 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.8449
- exerciser (mcfc): ran 0 of 5436 candidate functions (macro skipped 8, name skipped 10, scenario skipped 5418), errors 0, slow 0

## top partial-fallback reasons
- Missing space after argument @ function <functions:function>: 73
- Missing space after argument @ advancement grant <targets:entity_selector[players]> only <advancement:resource_location>: 17
- Failed to parse any options @ scoreboard objectives: 8

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:item#101].pos[0]`: base=0.41 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#102].pos[0]`: base=0.41 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#103].pos[0]`: base=0.41 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#104].pos[0]`: base=0.41 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#110].pos[0]`: base=0.42 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#111].pos[0]`: base=0.42 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#112].pos[0]`: base=0.42 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#113].pos[0]`: base=0.42 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#114].pos[0]`: base=0.42 | mcfc=0.43
- [final] `entities.minecraft:overworld.list[minecraft:item#126].pos[0]`: base=0.43 | mcfc=0.44
- [final] `entities.minecraft:overworld.list[minecraft:item#127].pos[0]`: base=0.43 | mcfc=0.44
- [final] `entities.minecraft:overworld.list[minecraft:item#128].pos[0]`: base=0.43 | mcfc=0.44
- [final] `entities.minecraft:overworld.list[minecraft:item#129].pos[0]`: base=0.43 | mcfc=0.44
- [final] `entities.minecraft:overworld.list[minecraft:item#130].pos[0]`: base=0.43 | mcfc=0.44
- [final] `entities.minecraft:overworld.list[minecraft:item#131].pos[0]`: base=0.43 | mcfc=0.45
- [final] `entities.minecraft:overworld.list[minecraft:item#132].pos[0]`: base=0.43 | mcfc=0.45
- [final] `entities.minecraft:overworld.list[minecraft:item#133].pos[0]`: base=0.43 | mcfc=0.45
- [final] `entities.minecraft:overworld.list[minecraft:item#134].pos[0]`: base=0.43 | mcfc=0.45
- [final] `entities.minecraft:overworld.list[minecraft:item#159].pos[0]`: base=0.45 | mcfc=0.46
- [final] `entities.minecraft:overworld.list[minecraft:item#160].pos[0]`: base=0.45 | mcfc=0.46
