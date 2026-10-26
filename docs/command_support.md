# Command support

This page lists how Function Compiler handles each command form, as of 0.4.0-alpha.2 (Minecraft 26.3).
The grammar is `data/vanilla.commands`; a command or form that is not in it is never parsed and always runs through vanilla.
**Compiled** means the form becomes native instructions. **Fallback** means it is parsed but runs through vanilla at that point. **Partly** means native for some inputs and fallback for others (the condition is given).
**store/return result** says whether the form can sit under `execute store result|success` or `return run`. `No` means the form does not report a result; if such a form is used there, the function cannot be compiled and runs through vanilla as a whole.

| Command form | Compiled | store/return result |
|---|---|---|
| `advancement grant/revoke <targets> everything` | Yes | No |
| `advancement grant/revoke <targets> only <adv> [<criterion>]` | Yes | No |
| `advancement grant/revoke <targets> from/through/until <adv>` | Yes | No |
| `clear [<targets> [<item> [<max>]]]` | Yes | Yes (fails when nothing was cleared) |
| `data get entity/storage <loc> [<path> [<scale>]]` | Partly: native for entity and storage, `block` falls back | Yes |
| `data merge entity/storage` | Yes | No |
| `data remove entity/storage` | Yes | No |
| `data modify <dest> <path> set/append/prepend value`, `insert <i> value` | Partly: native for storage destinations, entity and block fall back | Yes |
| `data modify <dest> <path> merge value` | Fallback | Yes |
| `data modify <dest> <path> set/append/prepend/insert ... from <src> [<path>]` | Fallback | Yes |
| `data modify <dest> <path> merge from <src> [<path>]` | Fallback | Yes |
| `data modify <dest> <path> set/append/prepend/insert ... string <src> <path> [<start> [<end>]]` | Fallback | Yes |
| `datapack enable/disable <name>` | Yes | No |
| `difficulty <difficulty>` | Yes | No |
| `effect give <targets> <effect> [<seconds> [<amplifier> [<hide>]]]` | Yes | Yes (fails when no entity was affected) |
| `effect clear [<targets> [<effect>]]` | Yes | No |
| `execute align/anchored/as/at/in/summon <...> <chain>` | Yes | Chain result comes from the final command |
| `execute on origin/target/vehicle/leasher/attacker/controller/owner/passengers <chain>` | Yes | Chain result comes from the final command |
| `execute positioned <pos>/as/over <...> <chain>` | Yes | Chain result comes from the final command |
| `execute rotated <rot>/as <...> <chain>` | Yes | Chain result comes from the final command |
| `execute run <command>` | Yes | Result of `<command>` |
| `execute if/unless entity <sel> <chain>` | Yes | Chain result comes from the final command |
| `execute if/unless entity <sel>` (no `run`) | Yes | Yes (`if`: match count, `unless`: 1; fails when the condition fails) |
| `execute if/unless score <t> <obj> matches <range> <chain>` | Yes | Chain result comes from the final command |
| `execute if/unless score <t> <obj> matches <range>` (no `run`) | Yes | Yes (1 or failure) |
| `execute if/unless score <a> <obj> <op> <b> <obj> <chain>` | Yes (needs a following command) | Chain result comes from the final command |
| `execute if/unless block <pos> <pred> <chain>` | Yes | Chain result comes from the final command |
| `execute unless biome <pos> <biome> <chain>` | Yes | Chain result comes from the final command |
| `execute if/unless dimension/loaded/predicate/function <...> <chain>` | Yes | Chain result comes from the final command |
| `execute if/unless data entity/storage <loc> <path> <chain>` | Yes | Chain result comes from the final command |
| `execute if/unless data block <pos> <path> <chain>` | Partly: the condition runs through vanilla, the chain stays compiled | Chain result comes from the final command |
| `execute if/unless blocks <a> <b> <dest> all/masked <chain>` | Partly: the condition runs through vanilla, the chain stays compiled | Chain result comes from the final command |
| `execute if/unless items entity/block ... <chain>` | Partly: the condition runs through vanilla, the chain stays compiled | Chain result comes from the final command |
| `execute if/unless items entity/block ...` (no `run`) | Fallback | Yes |
| `execute store result/success score <targets> <obj> <chain>` | Yes | Stores the result of the final command |
| `execute store result/success storage <id> <path> <type> <scale> <chain>` | Yes (`byte`, `short`, `int`, `long`, `float`, `double`) | Stores the result of the final command |
| `execute store result/success entity <sel> <path> <type> <scale> <chain>` | Fallback: from `store` to the end of the line, including the `run` command | Yes |
| `experience` / `xp add/set <targets> <amount> [points/levels]` | Yes | No |
| `fill <from> <to> <block> [replace [<filter>]/keep/destroy/hollow/outline]` | Yes | Yes |
| `function <id or #tag>` | Yes | Yes |
| `function <id> <compound>` | Yes | Yes |
| `function <id> with entity/storage <loc> [<path>]` | Yes | Yes |
| `function <id> with block <pos> [<path>]` | No: the function cannot be compiled and runs through vanilla | - |
| `gamemode <mode> [<targets>]` | Yes | No |
| `gamerule <rule> [<value>]` | Yes | Yes |
| `give <targets> <item> [<count>]` | Yes | No |
| `item modify entity/block ...` | Fallback | Yes |
| `item replace entity/block ... with <item>` | Fallback | Yes |
| `item replace entity/block ... from entity/block ...` | Fallback | Yes |
| `kill [<targets>]` | Partly: native unless the result is consumed, then fallback | Yes (through the fallback) |
| `loot replace/insert/give/spawn ... loot/kill/fish/mine ...` (all 22 forms) | Fallback | Yes |
| `particle <name> [<pos> [<delta> <speed> <count> [force/normal [<viewers>]]]]` | Yes | No |
| `playsound <sound> <source> <targets> [<pos> [<volume> [<pitch> [<min>]]]]` | Yes | No |
| `random value/roll <range> [<sequence>]` | Partly: native unless the range span is 0 or at least 2^31-1, then fallback | Yes |
| `random reset <sequence>`, `random reset *` | Yes | No |
| `recipe give/take <targets> <recipe or *>` | Yes | Yes (fails when nothing changed) |
| `return <value>`, `return fail` | Yes | Reports to the enclosing `execute store` |
| `return run <command>` | Yes | Result of `<command>` |
| `schedule function <id> <time> [append/replace]` | Partly: native unless the result is consumed, then fallback | Yes (through the fallback) |
| `schedule clear <id>` | Yes | Yes |
| `scoreboard objectives add <obj> <criteria>` | Yes | No |
| `scoreboard objectives add <obj> <criteria> <display>` | Partly: native when the display name is a text component, other strings fall back | Fallback form only |
| `scoreboard objectives remove/setdisplay ...` | Yes | No |
| `scoreboard players add/remove/set` | Yes | Yes |
| `scoreboard players get` | Yes | Yes |
| `scoreboard players operation` | Yes | Yes |
| `scoreboard players reset <targets> [<objective>]` | Yes | Yes |
| `scoreboard players enable` | Yes | No |
| `scoreboard players display name <targets> <objective>` | Yes (clears the name) | No |
| `scoreboard players display name <targets> <objective> <name>` | Partly: native for a text component, other strings fall back | Fallback form only |
| `scoreboard players display name <targets>` | Fallback | Yes |
| `scoreboard players display numberformat <targets> <objective> blank/styled/fixed` | Yes | No |
| `setblock <pos> <block> [keep/destroy]` | Yes | Yes |
| `stopsound <targets> [<source> [<sound>]]` | Yes | No |
| `summon <entity> [<pos> [<nbt>]]` | Yes | No |
| `tag <targets> add/remove <name>` | Yes | Yes (fails when no entity changed) |
| `team add/remove/empty/join/leave` | Yes | No |
| `team list [<team>]` | Fallback | No |
| `team modify <team> <option> <value>` | Partly: native for `color`, `friendlyFire`, `seeFriendlyInvisibles`, `nametagVisibility`, `deathMessageVisibility` and `collisionRule` with a valid value, everything else falls back | Fallback forms only |
| `tellraw <targets> <text>` | Partly: native unless the result is consumed, then fallback | Yes (through the fallback) |
| `time set/add/rate/pause/resume` | Yes | Yes |
| `time set <marker>` | Yes | Yes |
| `time query time/gametime/<timeline> [repetition]` | Yes | Yes |
| `time of <clock> <command>` | Fallback: the whole command, including the nested one | Yes |
| `title <players> title/subtitle/actionbar/times/clear/reset ...` | Yes | No |
| `tp` (all 11 forms, including `facing`) | Yes | No |
| `weather clear/rain/thunder [<duration>]` | Yes | No |

Grammar forms (`ID` lines of `data/vanilla.commands`): 191 compiled, 25 partly, 63 fallback, 279 in total.

## Not parsed (always vanilla)

These commands are not in the grammar, so every use runs through vanilla one command at a time:

`attribute`, `ban`, `bossbar`, `clone`, `damage`, `debug`, `defaultgamemode`, `dialog`, `enchant`, `fetchprofile`, `fillbiome`, `forceload`, `help`, `jfr`, `kick`, `list`, `locate`, `me`, `msg`, `perf`, `place`, `reload`, `ride`, `rotate`, `say`, `seed`, `setworldspawn`, `spawnpoint`, `spectate`, `spreadplayers`, `stopwatch`, `swing`, `teammsg`, `teleport`, `tell`, `test`, `tick`, `transfer`, `trigger`, `version`, `w`, `waypoint`, `worldborder`.

`tp` is parsed but its alias `teleport` is not. `xp` is an alias of `experience`.

Forms missing from commands that are otherwise parsed (they fall back on their own line):

- `execute facing ...`, `execute if biome ...`, `execute store ... block/bossbar ...`.
- Execute conditions without a following command, except `score ... matches`, `entity` and `items` (the only forms that can end a chain). For example `execute if score <a> <op> <b>`, `if block`, `if data` and `if function` need a command after them.
- `data merge block`, `data remove block`.
- `effect give ... infinite`, `datapack list`, `difficulty` without an argument, `experience query`, `tag <targets> list`.
- `item replace entity ... from block`, `loot replace ... fish/mine`.
- `scoreboard objectives list/modify`, `scoreboard players list`.

## Fallback behavior

- A top-level line the grammar cannot parse (unknown command, unsupported form, unsupported argument) becomes one vanilla fallback command. The rest of the function stays compiled.
- A command after `execute ... run` or `return run` that cannot be parsed falls back on its own. The `execute` chain around it stays compiled, and a `return run` keeps working. This applies only to the final command: an `execute` modifier that cannot be parsed sends the whole line to vanilla.
- A fallback gets the original source text of the command, including `^` coordinates, selector filters and quoting, never a rebuilt string.
- With `execute store` or `return run`, a fallback reports its vanilla result and success to the enclosing callback.
- A function normally compiles even when some commands fall back. The whole function runs through vanilla in these cases:
  - A `return` appears in command position of an unparsed command (top level or after `run`), or in a command that falls back with its nested command (`execute store ... entity`). Vanilla would swallow it. On a macro line, a placeholder at the start of the command or inside its `execute` part counts as a possible `return`.
  - The result of a form marked `No` is consumed by `execute store` or `return run`.
  - A form that cannot be compiled: `function ... with block`, a text component with `nbt` from a `block` source, or a `data modify ... set value` whose last path element is a filter.
  - A fallback has no recorded source text, or the generated bytecode fails verification.
- NBT paths are read as names, `[<index>]`, `[{filter}]` and `{filter}`. A path with `[]` is not parsed and the command falls back.
- Selectors that depend on vanilla-only resolution (for example UUID score holders or combined `type` options) are not compiled and the command falls back.

## Macros

A line that starts with `$` is a macro line. The `$` is removed and `$(name)` placeholders are read where the argument kind allows them. On lines without a leading `$`, `$(` is plain text.

Places that accept `$(name)`:

- Integer arguments marked `macro_int`: the amount of `experience add/set` and the score of `scoreboard players add/remove/set`. The whole argument must be one placeholder; `1$(x)` is rejected.
- Plain score holder names (not selectors, not `*`), alone or mixed with literal text, for example `$(player)_kills`. This covers the holder of every score accessor and the targets of `scoreboard players display ...` and `team join/leave`.
- The value of a selector `tag=` option, for example `@e[tag=$(tag)]`.
- Greedy trailing strings marked `[macros]`: the display name of `scoreboard objectives add` and `scoreboard players display name`, the `with <item>` of `item replace`, and the modifier of `item modify`. These forms always run through vanilla, which substitutes the placeholder.
- Slot sources of `item ...` and `execute if/unless items ...`.
- Any command that falls back keeps its placeholders. The substituted text is built at runtime from the function's macro arguments.

Everywhere else the reader fails with "Macros are not supported in this location" and the command (or the part after `run`) falls back to vanilla. This includes selectors other than `tag=`, objectives, resource locations, coordinates, rotations, NBT, block and item arguments, times and words. A text component (`tellraw`, `title`, display names parsed as text) fails with "Macro commands are not supported for json arguments" on any macro line, so those commands always run through vanilla there.
