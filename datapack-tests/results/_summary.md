# summary

columns: found/compiled/partial cmds/compile failures; confirmed/flaky/low-confidence findings; MSPT vanilla -> mcfc (median ms, solo runs)

| id | kind | status | found/compiled/partial/failures | confirmed/flaky/lowconf | MSPT vanilla -> mcfc | mcfc state |
|---|---|---|---|---|---|---|
| bacap | function | DIFF | 5436/5435/98/1 | 51/43/0 | 30.43 -> 29.97 | READY_ON |
| mr-veinminer | function | FALLBACK-ISSUE | 33/26/37/7 | 0/0/0 | 0.31 -> 0.3 | READY_ON |
| mr-tree-vein-miner | function | FALLBACK-ISSUE | 70/64/9/6 | 0/1/0 | 0.35 -> 0.33 | READY_ON |
| mr-timber-strike | function | FALLBACK-ISSUE | 51/46/95/5 | 0/0/0 | 0.3 -> 0.3 | READY_ON |
| mr-gardeners-dream | function | OK | 54/54/23/0 | 0/0/4 | 0.23 -> 0.24 | READY_ON |
| mr-spawn-animations | function | OK | 87/87/8/0 | 0/0/0 | 0.38 -> 0.38 | READY_ON |
| mr-orbital-strike-cannon | function | DIFF | 88/88/717/0 | 31/84/0 | 9.12 -> 7.93 | READY_ON |
| mr-armor-stand-arms | function | FALLBACK-ISSUE | 20/18/7/2 | 0/0/0 | 0.32 -> 0.3 | READY_ON |
| mr-terralith | worldgen | DIFF | 3/3/1/0 | 3/50/10 | 1.42 -> 1.16 | READY_ON |
| mr-tectonic | worldgen | DIFF | 0/0/0/0 | 4/16/13 | 0.46 -> 0.45 | READY_ON |
| mr-incendium | mixed | DIFF | 2314/2312/255/2 | 6/10/224 | 0.72 -> 0.87 | READY_ON |
| mr-nullscape | worldgen | DIFF | 9/7/19/2 | 2/11/11 | 0.63 -> 0.87 | READY_ON |
| mr-structory | worldgen | OK | 1/1/0/0 | 0/4/3 | 0.67 -> 0.61 | READY_ON |
| mr-dungeons-and-taverns | mixed | DIFF | 65/65/238/0 | 7/17/10 | 0.48 -> 0.49 | READY_ON |
| vt-multiplayer-sleep | function | OK | 33/33/17/0 | 0/0/0 | 0.28 -> 0.26 | READY_ON |
| vt-cauldron-concrete | function | OK | 2/2/0/0 | 0/1/0 | 0.25 -> 0.23 | READY_ON |
| vt-afk-display | function | OK | 5/5/0/0 | 0/0/0 | 0.21 -> 0.18 | READY_ON |
| vt-coordinates-hud | function | OK | 9/9/0/0 | 0/0/0 | 0.32 -> 0.25 | READY_ON |
| vt-durability-ping | function | OK | 106/106/0/0 | 0/0/0 | 0.24 -> 0.23 | READY_ON |
| vt-track-statistics | function | OK | 4/4/0/0 | 0/0/0 | 0.27 -> 0.22 | READY_ON |
| vt-track-raw-statistics | function | OK | 1/1/0/0 | 0/0/0 | 0.21 -> 0.22 | READY_ON |
| vt-armor-statues | function | OK | 72/72/1/0 | 0/1/0 | 0.4 -> 0.31 | READY_ON |
| vt-fast-leaf-decay | function | OK | 23/23/0/0 | 0/0/0 | 0.25 -> 0.22 | READY_ON |
| vt-graves | function | FALLBACK-ISSUE | 139/133/51/6 | 0/0/0 | 0.22 -> 0.19 | READY_ON |
| vt-homes | function | OK | 54/54/4/0 | 0/0/0 | 0.23 -> 0.21 | READY_ON |
| vt-tpa | function | OK | 19/19/0/0 | 0/0/0 | n/a | READY_ON |
| bacap-vt4 | combo | not run | - | - | - | - |
| vt-all | combo | not run | - | - | - | - |
| guns-terralith | combo | not run | - | - | - | - |
| mr-functions-all | combo | not run | - | - | - | - |
| worldgen-stack | combo | not run | - | - | - | - |
| kitchen-sink | combo | not run | - | - | - | - |

skipped: mr-guns_plus_plus (fails vanilla registry loading on 26.3 (recipes/advancements/predicate); mr-vanilla-refresh (fails vanilla registry loading on 26.3 (recipes/advancements/predicate)
