# vt-graves: FALLBACK-ISSUE

- kind: function, packs: vt-graves
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 118/139 functions compiled (84.9%), partial fallback commands: 51
- whole-function fallbacks: 0, compile failures: 21, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 4; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.2 ms (min 0.19, max 0.25); mcfc median 0.21 ms (min 0.17, max 0.26)
- mcfc debug: 6 compiled | 1 vanilla | 2 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.9819
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
