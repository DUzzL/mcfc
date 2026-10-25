# 0.4.0-alpha.2

Fixes bugs:
- With a mod that replaces `EntityLookup.add`/`remove` (Fabrolia cancels both at HEAD with its concurrent lookup), the per-type entity index stayed empty and kept killed entities: compiled `@e[type=…]` selectors found no entities (BlazeandCave checks, `execute as @e[type=…]`, `if entity`). The index now wraps both methods and re-checks the lookup afterwards, so it follows `byId` whatever implementation runs inside.

Performance:
- `@e[type=…]` no longer copies the whole per-type entity set on every evaluation; the index hands out one array snapshot per type, rebuilt only after an entity of that type was added or removed.
- `data get entity <target> Pos|Motion|Rotation[i] [scale]` (also under `execute store … run`) reads that one element from the entity instead of serializing the whole entity, which for a player includes inventory and ender chest. The value is the one `Entity.saveWithoutId` writes (a passenger's Pos holds the vehicle's X/Z, a shulker's Motion is zero, Rotation is `[yaw, pitch]` as floats); other paths and out-of-range indices still serialize. In the 2026-10-10 production profile the afk display pack's `remove_afk`/`check_afk` (six such reads per player every second) were the most expensive compiled functions.

Removed:
- Data reporting is gone: the reporter (`FunctionCompilerReporter` and its data records), its HTTP client and upload endpoint, and the `enable_reporting` config option. The mod no longer collects or sends timings, function counts, diagnostics or exceptions anywhere. Config files written by older versions are rewritten as version 3 without `enable_reporting`. Compilation counts and timings are logged locally in one `[Summary]` line after each load.
- The hard-disabled parallel datapack mode (`parallelFanout`, its `ForkJoinPool`, the mutation buffer, `ParallelSafetyAnalyzer`, the `PARALLEL_RUN_FUNCTION_AT_SELF` instruction and its design note). It could never run (`enable_parallel_datapacks` is ignored), and buffered mutations from pool threads do not fit server mods that tie entities to owning threads.

## Details
- **Released:** In Development
- **Supported Versions:** Minecraft 26.3

# 0.4.0-alpha.1

Updates to Minecraft 26.3 (Fabric Loader 0.19.5, Fabric API 0.161.0+26.3).

Minecraft 26.3 changes:
- The lazy `execute as` display-name mixin is removed: 26.3 resolves source names lazily itself (the mixin no longer applied and crashed startup).
- `time set|pause|resume|rate` and `gamerule` fail without side effects when nothing would change, like 26.3; `give` above 100 stacks fails and drops items like 26.3's `give`; `clear … 0` only counts.
- `recipe give|take` skip special recipes (including the new data-driven brewing recipes), like 26.3.
- `data merge|remove entity` run 26.3's post-load hook (villager trade prices).
- `@e[nbt={Invulnerable:…}]` only looks at permanent invulnerability (26.3 added temporary invulnerability).
- `random_chance` predicates read 26.3 float providers.
- The client version mixin targets a field that exists (it already crashed the client on 26.2).

Fixes bugs:
- `execute on leasher` ignores leashed boats on Minecraft 26.2, preventing BlazeandCave's Lead Zeppelin advancement from being awarded by compiled functions.
- `execute store result|success storage …`, `return run execute if|unless items …` and other result contexts always got result `0` / success `1` from `execute if|unless items`, regardless of whether items matched.
- `execute if|unless items` and `item replace|modify` mis-tokenized 26.3 inline SNBT slot sources containing spaces; `item replace … from entity` rejected the multi-entity sources that 26.3 accepts.
- Deep recursion between compiled functions overflowed the Java stack and aborted the call chain halfway; compiled calls now hand off to vanilla's iterative executor past a stack budget that charges each call by the calling function's frame size, so large function bodies hand off early enough too.
- Past that budget, `execute store … run function`, `execute if function` and `return run function` captured the last command's result of the callee instead of its `return` value; the hand-off now runs the callee like vanilla's `function` command.
- The compiler compiled and hashed a fresh read of every function file instead of the text vanilla had loaded, so editing a folder datapack and running `/mcfc recompile` (without `/reload`) ran different code compiled than through vanilla.
- `/mcfc recompile` kept the previous compilation's classes alive through functions that weren't called again.
- Development builds could load bytecode cached by an older build of the compiler.
- Vanilla-to-compiled function calls are faster when many different functions are called (function tags, advancement rewards): compiled functions are bound through generated dispatchers instead of method-handle proxies.
- Very long `execute` chains (a few dozen clauses) made the compiler run out of stack and send the whole function to vanilla.
- `/reload` (or `/mcfc recompile`) during a running compilation started overlapping compiles over shared state; compiles are now serialized, superseded ones are abandoned, and results are published on the server thread. `/stop` cancels a running compile.
- The compilation cache ignored function tags, advancements, predicates, entity tags and which functions vanilla loaded, reusing stale code; cache entries are now keyed by all of them, written atomically, checksummed, and a hit skips parsing.
- A compiled function that throws at runtime now logs once and runs through vanilla afterwards instead of logging on every call.
- A function ending in a line continuation no longer fails the whole compilation.
- A malformed `mcfc.json` is moved to `mcfc.json.bak` instead of being overwritten; `enable_parallel_datapacks` is ignored.
- `/mcfc show_diagnostics` printed the version instead of the count and flooded chat.
- `fill … replace <filter>` replaced the whole region unless the from-corner matched the filter; filters (block, tag, properties, NBT) are now tested on every position with vanilla's block predicate.
- `execute if|unless block`, `setblock`, `fill` and `execute positioned over` loaded or generated unloaded chunks; they now fail like vanilla, `fill` enforces `max_block_modifications`, and neighbours see the replaced block (levers, redstone, rails).
- `data get … <path>` truncated instead of flooring negative numbers; the scaled form now fails on non-numeric tags.
- `summon <entity> <pos> <nbt>` ran spawn finalization (random size, equipment, variant) over the given NBT and wrote `id` into the shared NBT constant; `execute summon` did not finalize mobs.
- `tp <targets> <pos>` with relative coordinates reset velocity and snapped the player's view; relative axes are now kept like vanilla and out-of-world destinations fail.
- `clear <targets> <item>` ignored item components, tags and `*`; it now uses vanilla's item predicate.
- NBT text components (`{"nbt":…}`) now honour `interpret`, `plain` and `separator`, use the full NBT path grammar and all selected entities.
- Vanilla-fallback commands are parsed once instead of on every execution.
- `playsound` reached players in other dimensions and drew a sound seed per player; `particle … force|normal` ignored the mode; `random roll` did not announce; invalid `random` ranges threw; `team modify … color reset` and component options were ignored; `data merge|remove entity` reloaded unchanged entities; `time query daytime` threw in dimensions without a default clock; unknown predicates matched when negated.
- Vanilla-fallback commands re-serialized their parsed arguments, losing `^` local coordinates, selector filters (rotations, advancements, multiple scores, entity type tags), quoting and integer block positions; fallbacks now run the original source text.
- `execute if|unless data block|blocks|items …` evaluate their condition through vanilla and compile the body natively, so a `return` in the body returns from the function instead of being swallowed.
- Absolute whole-number coordinates were centered wrongly: Y was centered and negative values moved the wrong way; rotations got +0.5°. Block-position arguments (`setblock`, `fill`, `if block`, `loot`, `item … block`, …) are now integers without centering.
- `recipe give|take` with anything other than `*` granted or revoked every recipe; `random reset *` never matched; `time of <clock>` ignored the clock.
- Macro placeholders in natively compiled string arguments (team modify, criteria, …) reached the command literally; macro score holders that expand to a selector or UUID were treated as fake-player names; `$(` in a non-macro line was substituted.
- SNBT numbers vanilla rejects (`01`, `200b`, `3000000000`, `1a`) or reads differently (`1e5`, `1i`) no longer silently become strings.
- Text components: SNBT booleans (`bold:1b`), `#RGB` / unknown colours, float `shadow_color`, and unsupported click/hover actions now behave like vanilla; legacy `clickEvent`/`hoverEvent` are ignored.
- Valid selectors such as `@e[type=!a,type=!b]` or `@e[type=player]` in player-only arguments were rejected (running through vanilla every time); `[single]` arguments and `limit`/`sort` rules now match vanilla.
- Grammar now enforces vanilla argument bounds (effect duration/amplifier, give count, playsound, particle, schedule, time set).
- Large functions were compiled into methods of 30–60 KB that the JVM never JIT-compiles (limit 8000 bytes) and `@s`-heavy or macro functions could exceed the 64 KB method limit; functions are now compiled in command chunks of at most ~6 KB each (macro functions included), which also bounds optimizer time per chunk.
- A single long `execute` chain (e.g. BlazeandCave's 42-clause `advancement/check/animal_kingdom`, 12.6 KB) still produced a method the JVM never JIT-compiles; the tail of such a chain is now compiled as a separate method that receives the chain's current source, splitting it into methods of at most ~7 KB.
- Split functions re-ran their leading side-effecting commands (`scoreboard objectives remove/add`, `data remove storage`, …) at the start of every chunk.
- One function that failed to build or verify disabled every function in its batch (and, through the cache, on every restart), or left compiled callers linked to a missing method; it now alone falls back to vanilla, classes are verified before they are defined, and the cache is written only after everything linked. A cache entry that fails to load now falls back to a full compile instead of failing.
- A function with two or more `@s` commands run without an entity (console, command blocks, `schedule`, `#load`/`#tick`) threw from the second command on instead of letting each command fail on its own; the optimizer no longer drops a null check that only an earlier command's failure path reaches.
- The optimizer took seconds to minutes on large functions (2,000 `@s` commands: 6 s; 4,000: 30 s); it now keeps indices stable while optimizing and answers dominance queries in O(1) (under 0.1 s for 4,000 commands).
- Functions using `@s[nbt=…]`/`@s[predicate=…]` or other boolean helper results failed to build ("invalid operation on type boolean").
- The bytecode emitter could reorder side-effecting calls or read a register after its source was overwritten; loading an undefined local now makes the function fall back to vanilla instead of producing unverifiable code.
- Rarely, the first compilation after startup failed until restart because two compile threads created instruction types at the same time.
- `execute if|unless entity …` and `limit=1` selectors (e.g. `data get entity @e[…,limit=1] …`) no longer allocate a result list on every execution.
- Data reporting (when enabled) could block forever on its first report.
- `scoreboard players add|remove|operation` used two score accesses, so a newly created score (e.g. `scoreboard players add @a kills 0`) was never sent to clients or saved; on read-only criteria (`health`, `xp`, …) they created the score and succeeded instead of failing like vanilla.
- `*` with no tracked score holders succeeded with result 0 instead of failing; `scoreboard players get *` and `execute if|unless score * …` resolved `*` instead of failing.
- Score commands on constant holders (`#fake` players) resolve their objective and score through link-time caches and run as one call without allocating; other holders read and write existing scores without allocating `ScoreAccess` objects.
- `scoreboard players operation A -= B` computed `B - A`; all operations now follow vanilla (floor division/modulo, division by zero fails, overflow wraps).
- `data modify storage … append|prepend|insert … value` did not modify the list; `data modify … set value` with an unchanged value now fails like vanilla.
- `effect give` without a duration was infinite instead of 30 seconds, and instant effects were applied 20 times.
- `execute store`, `return run` and `execute if|unless score|entity` as the last clause reported wrong results: `unless entity … run` skipped its body when success was stored, one failing source stopped the remaining sources, `store` wrote when nothing ran and summed across sources, `return run` with several sources ran later sources, and `store … run function #tag` stopped at the first function instead of summing like vanilla.
- `tag … add|remove` and `clear` always succeeded; they now report the number changed and fail on 0.
- `execute in <dimension>` did not scale X/Z by the dimension's coordinate scale.
- Selector `limit` was never applied (`give @p` gave to every player); box selectors (`dx`/`dy`/`dz`) applied `limit` before other filters; `x_rotation`/`y_rotation` ignored wrap-around ranges and defaults; empty `tag=`/`team=` were inverted; quoted or UUID targets (`kill "Name"`) and repeated selector options now behave like vanilla.
- Selectors iterate entities in vanilla order without per-tick snapshots, so entities moved, tagged or changed earlier in the same tick are seen correctly; `@e[type=…]` uses a per-type index kept in sync with vanilla's entity lookup; `execute on attacker` finds interaction entities and relations skip removed entities; `@e[distance=..30000000]` no longer hangs the server.
- `@e[nbt=…]` shortcuts for `Silent`, `NoGravity`, `Glowing`, `CustomNameVisible`, `MaxAir`, `MaxHealth`, `FallDistance` and empty `Tags` matched where vanilla does not.
- `tp … facing` reset velocity on relative axes, ignored world bounds and the source anchor; `time query <timeline>` succeeded for a timeline of another clock; `schedule function … 0t` ran the function; unknown biomes in `execute if biome` matched; particles for all players are sent as one packet; score text components with a selector matching several entities resolved instead of failing.
- Parse failures of runtime item/block predicates are re-checked after `/reload`.

## Details
- **Released:** In Development
- **Supported Versions:** None

# 0.3.2-alpha.2

Adds `selector` support in text

Adds macro support in:
- `xp` (all integer arguments)

Added config option:
- `profiler_collect_memory_usage` (default `true`) - Disables/Enables whether the profiler will collect memory usage (disabling this will make the profiler faster)

Fixes bugs:
- `execute unless score` does not execute the rest of the command if the score is not defined

## Details
- **Released:** 2025-03-16
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.2+1.21.4)

# 0.3.2-alpha.1

Adds selector argument: `nbt`

Adds commands:
- `function ... with entity ...`
- `execute store success storage ... double`
- `execute store success storage ... float`
- `execute store success storage ... int`
- `execute store success score`
- `return run`

Added storing result and success support to:
- `function` (for all currently supported sub-commands)
- `scoreboard players get` (`success` storing is new)
- `scoreboard players operation`

Adds a config file: `config/mcfc.json`, this file contains currently only contains the `enable_reporting` config option, which enables/disables telemetry

Adds `/mcfc` sub-command: `reload_config`

Fixes bugs:
- `execute on vehicle` does not get compiled

## Details
- **Released:** 2025-03-08
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.2-alpha.1+1.21.4)

# 0.3.1-alpha.2

Adds support for integer macros in `scoreboard players add|remove|set`

Fixes bugs:
- Macros don't get parsed and compiled properly
- `@n` selector looks for players instead of entities

## Details
- **Released:** 2025-02-23
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.2+1.21.4)

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
- **Released:** 2025-02-05
- **Supported Versions:** [1.19.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.19.4), [1.20–1.20.1](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.20.1), [1.20.2](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.20.2), [1.20.3–1.20.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.20.4), [1.20.5–1.20.6](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.20.6), [1.21–1.21.1](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.21), [1.21.2–1.21.3](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.21.2), [1.21.4](https://modrinth.com/mod/mcfc/version/0.3.1-alpha.1+1.21.4)

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