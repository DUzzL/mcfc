Not all commands are currently implemented in this mod, if a command is not listed at all, it means that it is not implemented at all.

| Command                                   | Supported | `store result` Support | `store success` Support |
|-------------------------------------------|-----------|------------------------|-------------------------|
| `data modify storage ... set value ...`   | Yes       | No                     | No                      |
| `difficulty`                              | Yes       | No                     | No                      |
| `execute align`                           | Yes       | No result by itself    | No result by itself     |
| `execute anchored`                        | Yes       | No result by itself    | No result by itself     |
| `execute as`                              | Yes       | No result by itself    | No result by itself     |
| `execute at`                              | Yes       | No result by itself    | No result by itself     |
| `execute if entity`                       | Yes       | No                     | No                      |
| `execute if function`                     | Yes       | No                     | No                      |
| `execute if score`                        | Yes       | No                     | No                      |
| `execute on target`                       | Yes       | No result by itself    | No result by itself     |
| `execute on vehicle`                      | Yes       | No result by itself    | No result by itself     |
| `execute positioned`                      | Yes       | No result by itself    | No result by itself     |
| `execute positioned as`                   | Yes       | No result by itself    | No result by itself     |
| `execute positioned over`                 | No        | No result by itself    | No result by itself     |
| `execute rotated`                         | Yes       | No result by itself    | No result by itself     |
| `execute rotated as`                      | Yes       | No result by itself    | No result by itself     |
| `execute run`                             | Yes       | No result by itself    | No result by itself     |
| `execute store ... storage ... double`    | Yes       | No result by itself    | No result by itself     |
| `execute store ... storage ... float`     | Yes       | No result by itself    | No result by itself     |
| `execute store ... storage ... int`       | Yes       | No result by itself    | No result by itself     |
| `execute store ... score`                 | Yes       | No result by itself    | No result by itself     |
| `execute unless entity`                   | Yes       | No                     | No                      |
| `execute unless function`                 | Yes       | No                     | No                      |
| `execute unless score`                    | Yes       | No                     | No                      |
| `experience add`                          | Yes       | No                     | No                      |
| `experience set`                          | Yes       | No                     | No                      |
| `function ... with block`                 | No        | No                     | No                      |
| `function ... with entity ...`            | Yes       | Yes                    | Yes                     |
| `function ... with entity ... <path>`     | Yes       | Yes                    | Yes                     |
| `function ... with storage ...`           | Yes       | Yes                    | Yes                     |
| `function ... with storage ... <path>`    | Yes       | Yes                    | Yes                     |
| `function ... <arguments>`                | Yes       | Yes                    | Yes                     |
| `function`                                | Yes       | Yes                    | Yes                     |
| `gamemode`                                | Yes       | No                     | No                      |
| `kill`                                    | Yes       | No                     | No                      |
| `return`                                  | Yes       | No                     | No                      |
| `scoreboard players add`                  | Yes       | No                     | No                      |
| `scoreboard players display name`         | No        | No                     | No                      |
| `scoreboard players display numberformat` | No        | No                     | No                      |
| `scoreboard players enable`               | Yes       | No                     | No                      |
| `scoreboard players get`                  | Yes       | Yes                    | Yes                     |
| `scoreboard players operation`            | Yes       | Yes                    | Yes                     |
| `scoreboard players remove`               | Yes       | No                     | No                      |
| `scoreboard players reset`                | Yes       | No                     | No                      |
| `scoreboard players set`                  | Yes       | No                     | No                      |
| `tag add`                                 | Yes       | No                     | No                      |
| `tag remove`                              | Yes       | No                     | No                      |
| `title ... actionbar`                     | Yes       | No                     | No                      |
| `title ... clear`                         | Yes       | No                     | No                      |
| `title ... reset`                         | Yes       | No                     | No                      |
| `title ... subtitle`                      | Yes       | No                     | No                      |
| `title ... times`                         | Yes       | No                     | No                      |
| `title ... title`                         | Yes       | No                     | No                      |
| `tellraw`                                 | Yes       | No                     | No                      |
| `weather`                                 | Yes       | No                     | No                      |
| `xp add`                                  | Yes       | No                     | No                      |
| `xp set`                                  | Yes       | No                     | No                      |

Partial support:
 - Only `/difficulty` with the difficulty argument is supported.
 - Only named `a.b`, and by index `a[0]` paths are allowed in NBT.
 - The following text components are not supported: `score`, `nbt`, and text styles `clickEvent`, `hoverEvent`.
 - Selectors don't support executing `nbt`, `predicate`, `advancement`
