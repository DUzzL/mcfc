Not all commands are currently implemented in this mod, if a command is not listed at all, it means that it is not implemented at all.

| Command                                   | Full Support | `store result` Support | `store success` Support |
|-------------------------------------------|--------------|------------------------|-------------------------|
| `advancement grant ... only ...`          | Yes          | No                     | No                      |
| `advancement revoke ... only ...`         | Yes          | No                     | No                      |
| `attribute base set`                      | Yes          | No                     | No                      |
| `data modify storage ... set value ...`   | Partial[^2]  | No                     | No                      |
| `difficulty`                              | Partial[^1]  | No                     | No                      |
| `effect`                                  | Yes          | No                     | No                      |
| `execute align`                           | Yes          | No result by itself    | No result by itself     |
| `execute anchored`                        | Yes          | No result by itself    | No result by itself     |
| `execute as`                              | Yes          | No result by itself    | No result by itself     |
| `execute at`                              | Yes          | No result by itself    | No result by itself     |
| `execute if entity`                       | Yes          | No                     | No                      |
| `execute if function`                     | Yes          | No                     | No                      |
| `execute if predicate`                    | Yes          | No                     | No                      |
| `execute if score`                        | Yes          | No                     | No                      |
| `execute on target`                       | Yes          | No result by itself    | No result by itself     |
| `execute on vehicle`                      | Yes          | No result by itself    | No result by itself     |
| `execute positioned`                      | Yes          | No result by itself    | No result by itself     |
| `execute positioned as`                   | Yes          | No result by itself    | No result by itself     |
| `execute positioned over`                 | No           | No result by itself    | No result by itself     |
| `execute rotated`                         | Yes          | No result by itself    | No result by itself     |
| `execute rotated as`                      | Yes          | No result by itself    | No result by itself     |
| `execute run`                             | Yes          | No result by itself    | No result by itself     |
| `execute store ... storage ... double`    | Partial[^2]  | No result by itself    | No result by itself     |
| `execute store ... storage ... float`     | Partial[^2]  | No result by itself    | No result by itself     |
| `execute store ... storage ... int`       | Partial[^2]  | No result by itself    | No result by itself     |
| `execute store ... score`                 | Yes          | No result by itself    | No result by itself     |
| `execute unless entity`                   | Yes          | No                     | No                      |
| `execute unless function`                 | Yes          | No                     | No                      |
| `execute unless predicate`                | Yes          | No                     | No                      |
| `execute unless score`                    | Yes          | No                     | No                      |
| `experience add`                          | Yes          | No                     | No                      |
| `experience set`                          | Yes          | No                     | No                      |
| `function ... with block`                 | No           | No                     | No                      |
| `function ... with entity ...`            | Yes          | Yes                    | Yes                     |
| `function ... with entity ... <path>`     | Partial[^2]  | Yes                    | Yes                     |
| `function ... with storage ...`           | Yes          | Yes                    | Yes                     |
| `function ... with storage ... <path>`    | Partial[^2]  | Yes                    | Yes                     |
| `function ... <arguments>`                | Yes          | Yes                    | Yes                     |
| `function`                                | Yes          | Yes                    | Yes                     |
| `gamemode`                                | Yes          | No                     | No                      |
| `kill`                                    | Yes          | No                     | No                      |
| `return`                                  | Yes          | No                     | No                      |
| `scoreboard players add`                  | Yes          | No                     | No                      |
| `scoreboard players display name`         | No           | No                     | No                      |
| `scoreboard players display numberformat` | No           | No                     | No                      |
| `scoreboard players enable`               | Yes          | No                     | No                      |
| `scoreboard players get`                  | Yes          | Yes                    | Yes                     |
| `scoreboard players operation`            | Yes          | Yes                    | Yes                     |
| `scoreboard players remove`               | Yes          | No                     | No                      |
| `scoreboard players reset`                | Yes          | No                     | No                      |
| `scoreboard players set`                  | Yes          | No                     | No                      |
| `tag add`                                 | Yes          | No                     | No                      |
| `tag remove`                              | Yes          | No                     | No                      |
| `title ... actionbar`                     | Partial[^3]  | No                     | No                      |
| `title ... clear`                         | Yes          | No                     | No                      |
| `title ... reset`                         | Yes          | No                     | No                      |
| `title ... subtitle`                      | Partial[^3]  | No                     | No                      |
| `title ... times`                         | Yes          | No                     | No                      |
| `title ... title`                         | Partial[^3]  | No                     | No                      |
| `tellraw`                                 | Partial[^3]  | No                     | No                      |
| `weather`                                 | Yes          | No                     | No                      |
| `xp add`                                  | Yes          | No                     | No                      |
| `xp set`                                  | Yes          | No                     | No                      |

[^1]: Only `/difficulty` with the difficulty argument is supported.
[^2]: Only named `a.b`, and by index `a[0]` paths are allowed.
[^3]: The following text components are not supported: `score`, `nbt`, and text styles `clickEvent`, `hoverEvent`.