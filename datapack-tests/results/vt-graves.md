# vt-graves: FALLBACK-ISSUE

- kind: function, packs: vt-graves
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 07:34:22)
- compile: 133/139 functions compiled (95.7%), partial fallback commands: 51
- whole-function fallbacks: 0, compile failures: 6, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 4; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.22 ms (min 0.2, max 0.26); mcfc median 0.19 ms (min 0.18, max 0.24)
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
