# summary

columns: found/compiled/partial cmds/compile failures; confirmed/flaky/low-confidence findings; MSPT vanilla -> mcfc (median ms, solo runs)

| id | kind | status | found/compiled/partial/failures | confirmed/flaky/lowconf | MSPT vanilla -> mcfc | mcfc state |
|---|---|---|---|---|---|---|
| bacap | function | OK | 5436/5436/98/0 | 0/0/37 | 32.97 -> 33.15 | READY_ON |
| mr-veinminer | function | OK | 33/33/36/0 | 0/0/0 | 0.34 -> 0.29 | READY_ON |
| mr-tree-vein-miner | function | OK | 70/70/6/0 | 0/0/4 | 0.39 -> 0.35 | READY_ON |
| mr-timber-strike | function | OK | 51/51/83/0 | 0/0/31 | 0.38 -> 0.3 | READY_ON |
| mr-gardeners-dream | function | OK | 54/54/23/0 | 0/0/0 | 0.23 -> 0.24 | READY_ON |
| mr-spawn-animations | function | OK | 87/87/8/0 | 0/0/0 | 0.43 -> 0.4 | READY_ON |
| mr-orbital-strike-cannon | function | DIFF | 88/88/1/0 | 1067/385/209 | 8.94 -> 8.58 | READY_ON |
| mr-armor-stand-arms | function | OK | 20/20/7/0 | 0/0/0 | 0.33 -> 0.42 | READY_ON |
| mr-terralith | worldgen | DIFF | 3/3/1/0 | 10/16/173 | 1.36 -> 1.1 | READY_ON |
| mr-tectonic | worldgen | OK | 0/0/0/0 | 0/1/1 | 0.52 -> 0.43 | READY_ON |
| mr-incendium | mixed | DIFF | 2314/2314/255/0 | 1/42/4 | 1.17 -> 1.35 | READY_ON |
| mr-nullscape | worldgen | DIFF | 9/9/17/0 | 1/15/7 | 1.1 -> 1.43 | READY_ON |
| mr-structory | worldgen | DIFF | 1/1/0/0 | 3/3/1 | 0.62 -> 0.69 | READY_ON |
| mr-dungeons-and-taverns | mixed | DIFF | 65/65/213/0 | 5/27/7 | 0.68 -> 0.69 | READY_ON |
| vt-multiplayer-sleep | function | OK | 33/33/17/0 | 0/0/0 | 0.29 -> 0.26 | READY_ON |
| vt-cauldron-concrete | function | OK | 2/2/0/0 | 0/0/0 | 0.24 -> 0.25 | READY_ON |
| vt-afk-display | function | OK | 5/5/0/0 | 0/0/0 | 0.24 -> 0.18 | READY_ON |
| vt-coordinates-hud | function | OK | 9/9/0/0 | 0/0/0 | 0.37 -> 0.27 | READY_ON |
| vt-durability-ping | function | OK | 106/106/0/0 | 0/0/0 | 0.23 -> 0.28 | READY_ON |
| vt-track-statistics | function | OK | 4/4/0/0 | 0/0/0 | 0.26 -> 0.22 | READY_ON |
| vt-track-raw-statistics | function | OK | 1/1/0/0 | 0/0/0 | 0.2 -> 0.21 | READY_ON |
| vt-armor-statues | function | OK | 72/72/1/0 | 0/0/0 | 0.25 -> 0.29 | READY_ON |
| vt-fast-leaf-decay | function | OK | 23/23/0/0 | 0/0/0 | 0.27 -> 0.23 | READY_ON |
| vt-graves | function | OK | 139/139/51/0 | 0/0/0 | 0.26 -> 0.31 | READY_ON |
| vt-homes | function | OK | 54/54/4/0 | 0/0/0 | 0.3 -> 0.26 | READY_ON |
| vt-tpa | function | OK | 19/19/0/0 | 0/0/0 | 0.19 -> 0.23 | READY_ON |
| bacap-vt4 | combo | OK | 5537/5537/119/0 | 0/0/122 | 19.75 -> 20.43 | READY_ON |
| vt-all | combo | OK | 467/467/73/0 | 0/0/0 | 0.45 -> 0.49 | READY_ON |
| guns-terralith | combo | DIFF | 54/54/84/0 | 3/8/9 | 2.22 -> 1.95 | READY_ON |
| mr-functions-all | combo | OK | 5839/5839/262/0 | 0/0/11 | 23.88 -> 21.07 | READY_ON |
| worldgen-stack | combo | DIFF | 2327/2327/273/0 | 3/6/11 | 1.99 -> 1.83 | READY_ON |
| kitchen-sink | combo | OK | 6060/6060/297/0 | 0/7/75 | 36.01 -> 30.26 | READY_ON |

skipped: mr-guns_plus_plus (fails vanilla registry loading on 26.3 (recipes/advancements/predicate); mr-vanilla-refresh (fails vanilla registry loading on 26.3 (recipes/advancements/predicate)
