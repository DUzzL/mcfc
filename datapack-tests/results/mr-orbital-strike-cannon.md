# mr-orbital-strike-cannon: DIFF

- kind: function, packs: mr-orbital-strike-cannon
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-1a611fce.jar sha256=0bb5961ab99af9ed3547bbde1d0ee2cdc6a0e61326a2286f938bc428fc7708d4 (file mtime 2026-10-26 09:56:09)
- compile: 88/88 functions compiled (100.0%), partial fallback commands: 1
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 1067, flaky 385, low-confidence 209, noise paths 10029
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 8.94 ms (min 8.93, max 9.24); mcfc median 8.58 ms (min 8.32, max 8.86)
- mcfc debug: 55 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 55 of 88 candidate functions (macro skipped 0, name skipped 1, scenario skipped 32), errors 0, slow 0
- note: mr-orbital-strike-cannon: pack_format patched (manifest): pack_format 108 -> 121; added min_format/max_format [121, 0] (required for formats above 81)

## top partial-fallback reasons
- Expected a selector: 1

## confirmed findings (first 20)
- [final] `entities.minecraft:overworld.list[minecraft:tnt#273].nbt`: base="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:76s}" | mcfc="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:78s}"
- [final] `entities.minecraft:overworld.list[minecraft:tnt#273].pos[0]`: base=-14.46 | mcfc=-14.09
- [final] `entities.minecraft:overworld.list[minecraft:tnt#273].pos[1]`: base=-8.02 | mcfc=-20.01
- [final] `entities.minecraft:overworld.list[minecraft:tnt#273].pos[2]`: base=133.86 | mcfc=-167.12
- [final] `entities.minecraft:overworld.list[minecraft:tnt#274].nbt`: base="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:85s}" | mcfc="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:76s}"
- [final] `entities.minecraft:overworld.list[minecraft:tnt#274].pos[0]`: base=-140.18 | mcfc=-14.46
- [final] `entities.minecraft:overworld.list[minecraft:tnt#274].pos[1]`: base=25.42 | mcfc=-8.02
- [final] `entities.minecraft:overworld.list[minecraft:tnt#274].pos[2]`: base=42.09 | mcfc=133.86
- [final] `entities.minecraft:overworld.list[minecraft:tnt#275].nbt`: base="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:83s}" | mcfc="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:85s}"
- [final] `entities.minecraft:overworld.list[minecraft:tnt#275].pos[0]`: base=-140.44 | mcfc=-140.18
- [final] `entities.minecraft:overworld.list[minecraft:tnt#275].pos[1]`: base=24.92 | mcfc=25.42
- [final] `entities.minecraft:overworld.list[minecraft:tnt#275].pos[2]`: base=-68.86 | mcfc=42.09
- [final] `entities.minecraft:overworld.list[minecraft:tnt#276].nbt`: base="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:84s}" | mcfc="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:83s}"
- [final] `entities.minecraft:overworld.list[minecraft:tnt#276].pos[0]`: base=-140.5 | mcfc=-140.44
- [final] `entities.minecraft:overworld.list[minecraft:tnt#276].pos[1]`: base=25.19 | mcfc=24.92
- [final] `entities.minecraft:overworld.list[minecraft:tnt#276].pos[2]`: base=-112.95 | mcfc=-68.86
- [final] `entities.minecraft:overworld.list[minecraft:tnt#277].nbt`: base="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:83s}" | mcfc="{Invulnerable:0b,block_state:\"minecraft:tnt\",fuse:84s}"
- [final] `entities.minecraft:overworld.list[minecraft:tnt#277].pos[0]`: base=-140.55 | mcfc=-140.5
- [final] `entities.minecraft:overworld.list[minecraft:tnt#277].pos[1]`: base=24.92 | mcfc=25.19
- [final] `entities.minecraft:overworld.list[minecraft:tnt#277].pos[2]`: base=-135.17 | mcfc=-112.95

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `entities.minecraft:overworld.list[minecraft:tnt#10].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#10].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#10].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].customName`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].nbt`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].pos[0]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].pos[1]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].pos[2]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].tags[]`
- [final] `entities.minecraft:overworld.list[minecraft:tnt#1114].type`

## low-confidence findings (209, first 10; not counted)
- `entities.minecraft:overworld.list[minecraft:item#1001].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1005].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1007].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#105].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#108].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1097].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#112].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1140].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1166].pos[1]`
- `entities.minecraft:overworld.list[minecraft:item#1175].pos[1]`
