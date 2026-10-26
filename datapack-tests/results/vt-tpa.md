# vt-tpa: OK

- kind: function, packs: vt-tpa
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-cd77d43f.jar sha256=b2a4923b93f4217a26c275c3c03f8f52795cc15b052ef828ec3841ad5690161b (file mtime 2026-10-26 09:06:07)
- compile: 19/19 functions compiled (100.0%), partial fallback commands: 0
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 0, flaky 0, low-confidence 0, noise paths 4
- exceptions (non-ignorable lines): base1=0, base2=2, mcfc=0
- mcfc debug: 18 compiled | 0 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 1.0
- exerciser (mcfc): ran 18 of 19 candidate functions (macro skipped 0, name skipped 1, scenario skipped 0), errors 0, slow 0

## exceptions base2
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://sessionserver.mojang.com/session/minecraft/profile/c8b0ccc87cd3306481b83317aa73ed1a?unsigned
- java.net.SocketTimeoutException x1: Read timed out
