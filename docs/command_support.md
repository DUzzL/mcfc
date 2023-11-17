Not all commands are currently implemented in this mod, if a command is not listed at all, it means that it is not implemented at all.

| Command                        | Full Support | `store result` Support | `store success` Support |
|--------------------------------|--------------|------------------------|-------------------------|
| `attribute base set`           | Partial[^1]  | No                     | No                      |
| `execute align`                | Yes          | No                     | No                      |
| `execute as`                   | Partial[^1]  | No                     | No                      |
| `execute at`                   | Partial[^1]  | No                     | No                      |
| `execute if entity`            | Partial[^1]  | No                     | No                      |
| `execute if score`             | Partial[^1]  | No                     | No                      |
| `execute positioned`           | Partial[^2]  | No                     | No                      |
| `execute positioned as`        | No           | No                     | No                      |
| `execute positioned over`      | No           | No                     | No                      |
| `execute store result score`   | Partial[^1]  | No                     | No                      |
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

[^1]: Not all selectors are currently supported…
[^2]: Local positions (^ ^ ^) are not implemented, relative and absolute positions do work.
[^3]: Functions do not accept macro arguments
