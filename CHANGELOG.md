# 0.3.1-alpha.1

Adds selector argument: `predicate`

Adds commands:
- `execute on origin`
- `execute on target`
- `execute store result storage ... float`
- `execute if predicate`
- `execute unless predicate`

Adds `/mcfc` sub-commands:
- `profiler`
  - `disable` - Disables profiler
  - `enable` - Starts profiling (possibly recompiles code)
  - `print_trace` - Prints last trace results in console
  - `print` - Prints profiler results in console
  - `reset` - Resets current results
  - `save_trace` - Saves last trace results to disk (`debug/` folder)
  - `save` - Saves profiler results to disk (`debug/` folder)
- `recompile` - Recompiles all functions
- `show_diagnostics` - Shows all errors from compilation

Fixes bugs:
- `execute positioned ^ ^ ^` works only when there's an entity in context
- Local coordinate math does not calculate the correct location
- `execute unless score ... matches a..b` generates an infinite loop

## Details
- **Released:** In Development
- **Supported Versions:** 1.19.4, 1.20–1.20.1, 1.20.2, 1.20.3–1.20.4, 1.20.5–1.20.6, 1.21–1.21.1, 1.21.2–1.21.3, 1.21.4

# 0.3.0-alpha.4

Re-adds support for some missing entity types

Adds selector argument: `advancements`

Adds commands:
- `advancement <grant|revoke> <players> only <advancement> [criterion]`
- `execute if function ...`
- `execute unless function ...`
- `execute store result storage ... double`
- `execute store result storage ... int` (now supports non-1 scale)

Fixes bugs:
- Texts with non-default styles don't compile (affects 1.21.4)
- `function ... with storage <storage> <path>` does not get run from within compiled functions
- `execute unless score ... <operator>` has flipped conditions

## Details
- **Released:** 2025-01-25
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.4+1.21.4)

# 0.3.0-alpha.3

Adds 1.19.4 and 1.21.4 support

Adds selector arguments: `dx`, `dy`, `dz`

## Details
- **Released:** 2025-01-21
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.3+1.21.4)

# 0.3.0-alpha.2

Adds 1.21.2 and 1.21.3 support

Adds selector arguments: `x`, `y`, `z`

Fixes bugs:
- Compilation throws an exception due to using a library in which a function doesn't exist in older minecraft versions
- `distance` selector compares locations without checking if the entity is in the same world
- Attributes and effects don't work due to a cast error while loading (affects 1.20.5+)
- Entities using the same classes under the hood all match the `[type]` selector (those entities are not supported anymore, for now)
- Some macros throw an exception due to generating wrong bytecode
- Nbt compounds with a suffix as a value without quotes break parsing (`b`, `d`, `f`, `l`, `s`)

## Details
- **Released:** 2025-01-19
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.2+1.21.2)

# 0.3.0-alpha.1

Update function compiler to have multiple compilation stages, for better code generation

Adds support for macros in `[tag]` selector

Fixes bugs:
- Function runs with wrong ordering when calling a vanilla function from compiled function (affects 1.20.3+)
- Translatable text arguments with custom style don't generate style data
- `execute if|unless function` inside vanilla functions while calling compiled function runs too many times (affects 1.20.3+)
- Forking behaves as on bedrock instead of java
- Not all functions are found, some of the time

## Details
- **Released:** 2025-01-12
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.1+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.1+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.1+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.0-alpha.1+1.21)

---

# 0.2.2-alpha.2

Adds commands:
- `scoreboard players enable`
- `title`
- `tellraw`

Adds base for macro support, macros are only supported for score holder names, for example `$scoreboard players set macro_$(test) objective 1`

Adds basic text support (only `text`, `keybind`, and `translate` component types, `hoverEvent` and `clickEvent` formatting options are also not supported)

Fixes bugs:
- `execute unless score` runs on the wrong conditions
- `scoreboard players operation` does not try creating the score if it doesn't exist

## Details
- **Released:** 2024-07-23
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.2alpha.2+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.2+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.2+1.20.6), [1.21](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.2+1.21)

---

# 0.2.2-alpha.1

Adds commands:
- `execute anchored`
- `execute on vehicle`
- `return <value>`
- `return fail`

Adds 1.21 support

Adds selector: `@n` (1.21 only)

Adds local coordinates

Adds `/mcfc` for enabling/disabling running compiled code, requires permission level 3 to use

## Details
- **Released:** 2024-07-17
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.2alpha.1+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.1+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.1+1.20.6), [1.21](https://modrinth.com/mod/mcfc/version/0.2.2-alpha.1+1.21)

---

# 0.2.1-alpha.6

Adds commands:
- `data modify storage <storage> <path> set value <value>`
- `execute store result storage <storage> <path> int 1`
- `function` (with argument support)

Adds selectors: `@p`, `@r`

Adds selector argument: `sort`

Adds nbt support internally

Adds nbt paths: `foo.bar`, `foo[0]`

Adds a better instruction optimizer

Functions compile in the background now

Fixes bugs:
- `@e` returned dead players
- Commands which remove entities from the world broke selectors
- `execute align` does not work
- Using `/function` in non-compiled functions does not run the compiled variant (affects 1.20.4+)

## Details
- **Released:** 2024-07-12
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.6+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.6+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.6+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.6+1.20.6)

---

# 0.2.1-alpha.5

Fixes bugs:
- The game crashed after a attribute that isn't a `RangedAttribute` was loaded

## Details
- **Released:** 2024-07-07
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.5+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.5+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.5+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.5+1.20.6)

---

# 0.2.1-alpha.4

Adds commands:
- `effect`
- `experience add`/`xp add`
- `experience set`/`xp set`

Adds selector arguments: `distance`, `level`, `name`

Fixes bugs:
- `gamemode` selector overwrites previous `gamemode` selectors
- Rotations with relatives angles cause an exception at runtime (affects `execute rotated <rotation>` only)

## Details
- **Released:** 2024-07-06
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.4+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.4+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.4+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.4+1.20.6)

---

# 0.2.1-alpha.3

Adds commands:
- `execute rotated as`
- `execute rotated`
- `execute positioned as`

Fixes bugs:
- `team` selector overwrites previous `team` selectors
- `team` selector fails to compare team names properly
- Positions fail to parse with a `~` without a number after it
- Using objectives which don't exist causes an exception when running the command
- Using a command that accepts a scoreholder can break at compile time (affects `execute if score` only)
- Comparing two scores together reverses the operator (`a <= b` became `b <= a` in compiled code)
- 1.20.1 code fails to call external functions due to missing access widener entry
- Some selectors fail to parse properly with whitespace

## Details
- **Released:** 2024-07-04
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.3+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.3+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.3+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.3+1.20.6)

---

# 0.2.1-alpha.2

Adds commands:
- `difficulty <peaceful|easy|normal|hard>`
- `weather`

Improved stability

## Details
- **Released:** 2024-07-03
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.2+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.2+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.2+1.20.6)

---

# 0.2.1-alpha.1

- Removed Kotlin from dependencies
- Fixed some bugs

## Details
- **Released:** 2024-07-02
- **Supported Versions:** [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.1+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.1+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.2.1-alpha.1+1.20.6)

---

# 0.2.0-alpha.3

Adds commands:
- `scoreboard players reset`

Adds selector arguments: `x_rotation`, `y_rotation`

## Details
- **Released:** 2023-11-27
- **Supported Versions:** [1.20.1](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.3+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.3+1.20.2)

---

# 0.2.0-alpha.2

- Add telemetry for diagnostics and exceptions

## Details
- **Released:** 2023-11-21
- **Supported Versions:** [1.20.1](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.2+1.20.2)

---

# 0.2.0-alpha.1

- Updated codebase to be more modular (adds 1.20.1 support :D)

## Details
- **Released:** 2023-11-19
- **Supported Versions:** [1.20.1](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.2.0-alpha.1+1.20.2)

---

# 0.1.0-alpha.7

Adds commands:
- `attribute <target> <attribute> base set`
- `execute store result score`
- `scoreboard players get`

## Details
- **Released:** 2023-11-17
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.7+1.20.2)

---

# 0.1.0-alpha.6

Adds commands:
- `execute align`
- `gamemode`

Adds selector arguments: `gamemode`, `team`, `type`

## Details
- **Released:** 2023-11-11
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.6+1.20.2)

---

# 0.1.0-alpha.5

Adds commands:
- `execute positioned <pos>`

Adds selector arguments: `scores`

## Details
- **Released:** 2023-11-09
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.5+1.20.2)

---

# 0.1.0-alpha.4

Fixed compatibility with the game outside of the development environment (oops)

Adds commands:
- `execute at`

## Details
- **Released:** 2023-11-07
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.4+1.20.2)

---

# 0.1.0-alpha.3

Rewritten compiler backend to allow support for better optimizations in the future and better support for other commands

## Details
- **Released:** 2023-11-04
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.3+1.20.2)

---

# 0.1.0-alpha.2

Adds commands:
- `execute unless entity`
- `execute unless score`
- `scoreboard players operation /=`
- `scoreboard players operation %=`
- `scoreboard players operation <`
- `scoreboard players operation >`
- `scoreboard players operation ><`

## Details
- **Released:** 2023-10-30
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.2+1.20.2)

---

# 0.1.0-alpha.1

Adds commands:
- `execute as`
- `execute if entity`
- `execute if score`
- `execute run`
- `function` (without arguments)
- `kill`
- `scoreboard players add`
- `scoreboard players operation =`
- `scoreboard players operation +=`
- `scoreboard players operation -=`
- `scoreboard players operation *=`
- `scoreboard players remove`
- `scoreboard players reset` (no objective)
- `scoreboard players set`
- `tag add`
- `tag remove`

Adds selectors: player name, UUID, `@a`, `@e`, `@s`

Adds selector arguments: `limit`, `tag`

## Details
- **Released:** 2023-10-29
- **Supported Versions:** [1.20.2](https://modrinth.com/mod/mcfc/version/0.1.0-alpha.1+1.20.2)