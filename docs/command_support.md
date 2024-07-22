Not all commands are currently implemented in this mod, if a command is not listed at all, it means that it is not implemented at all.

| Command                                   | Full Support | `store result` Support | `store success` Support |
|-------------------------------------------|--------------|------------------------|-------------------------|
| `attribute base set`                      | Partial[^1]  | No                     | No                      |
| `data modify storage ... set value ...`   | Partial[^3]  | No                     | No                      |
| `difficulty`                              | Partial[^2]  | No                     | No                      |
| `effect`                                  | Partial[^1]  | No                     | No                      |
| `execute align`                           | Yes          | No result by itself    | No result by itself     |
| `execute anchored`                        | Yes          | No result by itself    | No result by itself     |
| `execute as`                              | Partial[^1]  | No result by itself    | No result by itself     |
| `execute at`                              | Partial[^1]  | No result by itself    | No result by itself     |
| `execute if entity`                       | Partial[^1]  | No                     | No                      |
| `execute if score`                        | Partial[^1]  | No                     | No                      |
| `execute on vehicle`                      | Yes          | No result by itself    | No result by itself     |
| `execute positioned`                      | Yes          | No result by itself    | No result by itself     |
| `execute positioned as`                   | Partial[^1]  | No result by itself    | No result by itself     |
| `execute positioned over`                 | No           | No result by itself    | No result by itself     |
| `execute rotated`                         | Yes          | No result by itself    | No result by itself     |
| `execute rotated as`                      | Partial[^1]  | No result by itself    | No result by itself     |
| `execute run`                             | Yes          | No result by itself    | No result by itself     |
| `execute store result storage ... int 1`  | Partial[^3]  | No result by itself    | No result by itself     |
| `execute store result score`              | Partial[^1]  | No result by itself    | No result by itself     |
| `execute unless entity`                   | Partial[^1]  | No                     | No                      |
| `execute unless score`                    | Partial[^1]  | No                     | No                      |
| `experience add`                          | Partial[^1]  | No                     | No                      |
| `experience set`                          | Partial[^1]  | No                     | No                      |
| `function ... with block`                 | No           | No                     | No                      |
| `function ... with entity`                | No           | No                     | No                      |
| `function ... with storage ...`           | Yes          | No                     | No                      |
| `function ... with storage ... <path>`    | Partial[^3]  | No                     | No                      |
| `function ... <arguments>`                | Yes          | No                     | No                      |
| `function`                                | Yes          | No                     | No                      |
| `gamemode`                                | Partial[^1]  | No                     | No                      |
| `kill`                                    | Partial[^1]  | No                     | No                      |
| `return <value>`                          | Yes          | No                     | No                      |
| `return fail`                             | Yes          | No                     | No                      |
| `return run`                              | No           | No                     | No                      |
| `scoreboard players add`                  | Partial[^1]  | No                     | No                      |
| `scoreboard players display name`         | No           | No                     | No                      |
| `scoreboard players display numberformat` | No           | No                     | No                      |
| `scoreboard players enable`               | Partial[^1]  | No                     | No                      |
| `scoreboard players get`                  | Partial[^1]  | Yes                    | No                      |
| `scoreboard players operation`            | Partial[^1]  | No                     | No                      |
| `scoreboard players remove`               | Partial[^1]  | No                     | No                      |
| `scoreboard players reset`                | Partial[^1]  | No                     | No                      |
| `scoreboard players set`                  | Partial[^1]  | No                     | No                      |
| `tag add`                                 | Partial[^1]  | No                     | No                      |
| `tag remove`                              | Partial[^1]  | No                     | No                      |
| `tellraw`                                 | Partial[^4]  | No                     | No                      |
| `weather`                                 | Yes          | No                     | No                      |
| `xp add`                                  | Partial[^1]  | No                     | No                      |
| `xp set`                                  | Partial[^1]  | No                     | No                      |

[^1]: Not all selectors are currently supported.
[^2]: Only `/difficulty` with the difficulty argument is supported.
[^3]: Only named `a.b`, and by index `a[0]` paths are allowed.
[^4]: The following text components are not supported: `score`, `nbt`, `selector`.