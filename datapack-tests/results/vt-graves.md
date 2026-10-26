# vt-graves: OK

- kind: function, packs: vt-graves
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 139/139 functions compiled (100.0%), partial fallback commands: 51
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 0; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.26 ms (min 0.23, max 0.32); mcfc median 0.31 ms (min 0.28, max 0.32)
- mcfc debug: 6 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 115 of 139 candidate functions (macro skipped 17, name skipped 7, scenario skipped 0), errors 113, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 30
- Unknown command: rotate: 11
- Unknown command: ride: 2
- Invalid integer: 2
- Unknown command: forceload: 2
- Too many arguments for @ loot spawn <pos:vec3> loot <loot_table:resource_location>: 2
- Expected '{' while reading nbt compound, but got '$': 1
- Macro commands are not supported for json arguments: 1
