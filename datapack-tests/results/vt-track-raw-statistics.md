# vt-track-raw-statistics: OK

- kind: function, packs: vt-track-raw-statistics
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-0.4.0-alpha.2+26.3-fabric.jar (file mtime 2026-10-26 03:26:36)
- compile: 1/1 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 2; not re-run (no findings)
- exceptions (non-ignorable lines): base1=2, base2=0, mcfc=0
- MSPT (solo, sprint 1200 x3): vanilla median 0.21 ms (min 0.19, max 0.26); mcfc median 0.22 ms (min 0.21, max 0.28)
- mcfc debug: 1 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 1 of 1 candidate functions (macro skipped 0, name skipped 0, scenario skipped 0), errors 0, slow 0

## exceptions base1
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/c8b0ccc87cd3306481b83317aa73ed1a?unsigned
- java.net.SocketTimeoutException x1: Read timed out
