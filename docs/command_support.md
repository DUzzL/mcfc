Not all commands are currently implemented in this mod, if a command is not listed at all, it means that it is not implemented at all.

| Command                        | Full Support | `store result` Support | `store success` Support |
|--------------------------------|--------------|------------------------|-------------------------|
| `attribute base set`           | Partial[^1]  | No                     | No                      |
| `difficulty`                   | Partial[^4]  | No                     | No                      |
| `effect`                       | Partial[^1]  | No                     | No                      |
| `experience add`               | Partial[^1]  | No                     | No                      |
| `experience set`               | Partial[^1]  | No                     | No                      |
| `execute align`                | Yes          | No result by itself    | No result by itself     |
| `execute as`                   | Partial[^1]  | No result by itself    | No result by itself     |
| `execute at`                   | Partial[^1]  | No result by itself    | No result by itself     |
| `execute if entity`            | Partial[^1]  | No                     | No                      |
| `execute if score`             | Partial[^1]  | No                     | No                      |
| `execute positioned`           | Partial[^2]  | No result by itself    | No result by itself     |
| `execute positioned as`        | Partial[^1]  | No result by itself    | No result by itself     |
| `execute positioned over`      | No           | No result by itself    | No result by itself     |
| `execute rotated`              | Yes          | No result by itself    | No result by itself     |
| `execute rotated as`           | Partial[^1]  | No result by itself    | No result by itself     |
| `execute run`                  | Yes          | No result by itself    | No result by itself     |
| `execute store result score`   | Partial[^1]  | No result by itself    | No result by itself     |
| `execute unless entity`        | Partial[^1]  | No                     | No                      |
| `execute unless score`         | Partial[^1]  | No                     | No                      |
| `function`                     | Partial[^3]  | No                     | No                      |
| `gamemode`                     | Partial[^1]  | No                     | No                      |
| `kill`                         | Partial[^1]  | No                     | No                      |
| `scoreboard players add`       | Partial[^1]  | No                     | No                      |
| `scoreboard players enable`    | No           | No                     | No                      |
| `scoreboard players get`       | Partial[^1]  | Yes                    | No                      |
| `scoreboard players operation` | Partial[^1]  | No                     | No                      |
| `scoreboard players remove`    | Partial[^1]  | No                     | No                      |
| `scoreboard players reset`     | Partial[^1]  | No                     | No                      |
| `scoreboard players set`       | Partial[^1]  | No                     | No                      |
| `tag add`                      | Partial[^1]  | No                     | No                      |
| `tag remove`                   | Partial[^1]  | No                     | No                      |
| `weather`                      | Yes          | No                     | No                      |
| `xp add`                       | Partial[^1]  | No                     | No                      |
| `xp set`                       | Partial[^1]  | No                     | No                      |

[^1]: Not all selectors are currently supported.
[^2]: Local positions (^ ^ ^) are not implemented, relative and absolute positions do work.
[^3]: Functions do not accept macro arguments
[^4]: Only `/difficulty` with the difficulty argument is supported
