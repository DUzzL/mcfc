# summary

columns: found/compiled/partial cmds/compile failures; confirmed/flaky/low-confidence findings; MSPT vanilla -> mcfc (median ms, solo runs)

| id | kind | status | found/compiled/partial/failures | confirmed/flaky/lowconf | MSPT vanilla -> mcfc | mcfc state |
|---|---|---|---|---|---|---|
| bacap | function | OK | 5436/5436/98/0 | 0/0/37 | 32.97 -> 33.15 | READY_ON |
| mr-veinminer | function | FALLBACK-ISSUE | 33/27/36/6 | 0/0/0 | 0.33 -> 0.27 | READY_ON |
| mr-tree-vein-miner | function | FALLBACK-ISSUE | 70/68/6/2 | 0/0/10 | 0.33 -> 0.35 | READY_ON |
| mr-timber-strike | function | FALLBACK-ISSUE | 51/50/83/1 | 0/0/4 | 0.28 -> 0.3 | READY_ON |
| mr-gardeners-dream | function | OK | 54/54/23/0 | 0/0/0 | 0.23 -> 0.24 | READY_ON |
| mr-spawn-animations | function | OK | 87/87/8/0 | 0/0/0 | 0.43 -> 0.4 | READY_ON |
| mr-orbital-strike-cannon | function | DIFF | 88/88/1/0 | 1067/385/209 | 8.94 -> 8.58 | READY_ON |
| mr-armor-stand-arms | function | FALLBACK-ISSUE | 20/19/7/1 | 0/0/0 | 0.28 -> 0.24 | READY_ON |
| mr-terralith | worldgen | DIFF | 3/3/1/0 | 10/16/173 | 1.36 -> 1.1 | READY_ON |
| mr-tectonic | worldgen | OK | 0/0/0/0 | 0/1/1 | 0.52 -> 0.43 | READY_ON |
| mr-incendium | mixed | DIFF | 2314/2312/255/2 | 2/26/13 | 0.85 -> 0.83 | READY_ON |
| mr-nullscape | worldgen | FALLBACK-ISSUE | 9/8/17/1 | 0/14/2 | 0.75 -> 0.61 | READY_ON |
| mr-structory | worldgen | DIFF | 1/1/0/0 | 3/3/1 | 0.62 -> 0.69 | READY_ON |
| mr-dungeons-and-taverns | mixed | DIFF | 65/65/213/0 | 10/25/16 | 0.57 -> 0.47 | READY_ON |
| vt-multiplayer-sleep | function | OK | 33/33/17/0 | 0/0/0 | 0.29 -> 0.26 | READY_ON |
| vt-cauldron-concrete | function | OK | 2/2/0/0 | 0/0/0 | 0.24 -> 0.25 | READY_ON |
| vt-afk-display | function | OK | 5/5/0/0 | 0/0/0 | 0.24 -> 0.18 | READY_ON |
| vt-coordinates-hud | function | OK | 9/9/0/0 | 0/0/0 | 0.37 -> 0.27 | READY_ON |
| vt-durability-ping | function | OK | 106/106/0/0 | 0/0/0 | 0.23 -> 0.28 | READY_ON |
| vt-track-statistics | function | OK | 4/4/0/0 | 0/0/0 | 0.26 -> 0.22 | READY_ON |
| vt-track-raw-statistics | function | OK | 1/1/0/0 | 0/0/0 | 0.2 -> 0.21 | READY_ON |
| vt-armor-statues | function | OK | 72/72/1/0 | 0/0/0 | 0.25 -> 0.29 | READY_ON |
| vt-fast-leaf-decay | function | OK | 23/23/0/0 | 0/0/0 | 0.27 -> 0.23 | READY_ON |
| vt-graves | function | FALLBACK-ISSUE | 139/136/51/3 | 0/0/0 | 0.23 -> 0.21 | READY_ON |
| vt-homes | function | OK | 54/54/4/0 | 0/0/0 | 0.3 -> 0.26 | READY_ON |
| vt-tpa | function | DIFF | 19/19/0/0 | 4/0/0 | 0.15 -> 0.17 | READY_ON |
| bacap-vt4 | combo | OK | 5537/5537/119/0 | 0/0/122 | 19.75 -> 20.43 | READY_ON |
| vt-all | combo | FALLBACK-ISSUE | 467/464/73/3 | 0/1/0 | 0.57 -> 0.58 | READY_ON |
| guns-terralith | combo | DIFF | 54/53/84/1 | 33/6/5 | 2.13 -> 2.04 | READY_ON |
| mr-functions-all | combo | FALLBACK-ISSUE | 5839/5829/262/10 | 0/0/12 | 17.91 -> 20.33 | READY_ON |
| worldgen-stack | combo | DIFF | 2327/2324/273/3 | 4/36/3 | 1.73 -> 1.86 | READY_ON |
| kitchen-sink | combo | DIFF | 6060/6048/297/12 | 2/10/28 | 24.12 -> 23.19 | READY_ON |

skipped: mr-guns_plus_plus (fails vanilla registry loading on 26.3 (recipes/advancements/predicate); mr-vanilla-refresh (fails vanilla registry loading on 26.3 (recipes/advancements/predicate)
