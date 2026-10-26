# vt-fast-leaf-decay: OK

- kind: function, packs: vt-fast-leaf-decay
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 23/23 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 21; not re-run (no findings)
- exceptions (non-ignorable lines): base1=0, base2=2, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.25 ms (min 0.19, max 0.29); mcfc median 0.22 ms (min 0.16, max 0.26)
- mcfc debug: 12 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 21 of 23 candidate functions (macro skipped 0, name skipped 2, scenario skipped 0), errors 11, slow 0

## exceptions base2
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/3c60c0c23d05399fbffb864ec993da33?unsigned
- java.net.SocketTimeoutException x1: Read timed out
