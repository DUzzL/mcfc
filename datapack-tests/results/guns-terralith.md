# guns-terralith: DIFF

- kind: worldgen, packs: mr-timber-strike, mr-terralith
- mcfc final state: READY_ON (after load: READY_ON)
- mcfc jar: mcfc-retfix.jar sha256=5f2c1a7e1971f44c8106c7472ecb704e1ef97f5761eb41aa8226e49adffd1792 (file mtime 2026-10-26 15:02:24)
- compile: 54/54 functions compiled (100.0%), partial fallback commands: 84
- whole-function fallbacks: 0, compile failures: 0, runtime disables: 0
- findings: confirmed 3, flaky 8, low-confidence 9, noise paths 1078; worldgen (gen dump) findings 10
- exceptions (non-ignorable lines): base1=0, base2=0, mcfc=2
- MSPT (solo, sprint 1200 x3): vanilla median 2.22 ms (min 2.16, max 3.92); mcfc median 1.95 ms (min 1.76, max 2.18)
- mcfc debug: 44 compiled | 1 vanilla | 0 errors | 0 too_big | 0 mcfc_off; compiled-call share 0.996
- exerciser (mcfc): ran 44 of 54 candidate functions (macro skipped 9, name skipped 1, scenario skipped 0), errors 0, slow 0

## top partial-fallback reasons
- Macros are not supported in this location: 67
- Expected a selector: 15
- Too many arguments for @ scoreboard players reset <targets:score_holder>: 1
- Unknown command: spreadplayers: 1

## confirmed findings (first 20)
- [gen] `blocks[overworld].columns.-6,-2`: base="0b6c7a50872bdae296dc86ebf0437b41491c2b030d13e55743c2eac3150764ac" | mcfc="75989c00d8a616ff44f39ba8972fc3ce786606fd1d7e6b311cbd94332b45804d"
- [gen] `blocks[overworld].columns.-7,-2`: base="b965c94a1336e7eefc21c54375b342b4cee81c4827b710c6fd87bc6eb6c6b60f" | mcfc="4e467481874e075acbf10ee81d204a4e07399adb588e837260809608d63fecff"
- [gen] `blocks[overworld].columns.-8,3`: base="4bd70e123a6c371f7bd3f11ddf4d4c3d8beb0ed621b9784e9eafa0171fc6b2bb" | mcfc="c6e72e8e3273481d1759664db02861cdba0bcc02b4767f59663131845b63c012"

## flaky findings (first 10, not reproduced in the second attempt)
- [final] `blocks[overworld].columns.6,5`
- [gen] `blocks[overworld].columns.-3,1`
- [gen] `blocks[overworld].columns.-3,3`
- [gen] `blocks[overworld].columns.-3,4`
- [gen] `blocks[overworld].columns.3,2`
- [gen] `blocks[overworld].columns.3,3`
- [gen] `blocks[overworld].columns.6,5`
- [gen] `blocks[overworld].columns.6,6`

## low-confidence findings (9, first 10; not counted)
- `blocks[overworld].columns.-3,1`
- `blocks[overworld].columns.-3,3`
- `blocks[overworld].columns.-3,4`
- `blocks[overworld].columns.-6,-2`
- `blocks[overworld].columns.-7,-2`
- `blocks[overworld].columns.-8,3`
- `blocks[overworld].columns.3,2`
- `blocks[overworld].columns.3,3`
- `blocks[overworld].columns.6,6`

## exceptions mcfc
- com.mojang.authlib.exceptions.MinecraftClientException x1: Failed to read from https://api.mojang.com/users/profiles/minecraft/dptester1 due to Read timed out
- java.net.SocketTimeoutException x1: Read timed out
