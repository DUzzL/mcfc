# mcfc architecture notes

Short map of the compiler for the datapack test work. Paths: `C` = `common/src/main/java/dev/epicpix/minecraftfunctioncompiler`, `M` = `mc-26-1-fabric/src/main/java/dev/epicpix/minecraftfunctioncompiler/mod`.

## Load path
- `M/mixin/ServerFunctionLibraryMixin` captures the raw lines of every function vanilla accepted. Functions vanilla rejects are never compiled.
- `M/mixin/ServerFunctionManagerMixin.postReload` creates a `M/FunctionClassLoader` (subclass of `C/loader/FunctionCompilerClassLoader`) per reload. Compilation runs async on `LowPriorityCompileExecutor`. A newer reload cancels an older one. Game tests force blocking compilation (`TestEnvironment`).
- Results are cached in `<gameDir>/generated/cache/<hash>/` (hash over sources, registries, mod and MC version). The newest 4 entries are kept.

## Parsing
- `FunctionCodeGenerator.loadFunctionSources` joins `\` continuations and drops comments. `parseFunctions` parses each line with the KSP-generated `CommandParser`.
- The grammar comes from `data/vanilla.commands`. IL instructions come from `data/data.instructions`. Both are turned into Java by `annotation-processor` (KSP).
- Argument parsers are hand-written: `StringCommandReader`, `NbtParser`, `SelectorParser`, `TextParser`.

## Fallback levels
1. Per command at parse time: an unknown or unparsable command becomes `CommandFallbackVanilla`. The function still compiles; that one command runs through vanilla. Logged as one WARN summary `Partially compiled N command(s) with vanilla fallback. Reasons:` grouped by message.
2. Per command at compile time: `CommandCompiler` emits `emitVanillaFallback` for parsed but unsupported forms. A raw fallback that may contain `return` throws and makes the whole function fall back.
3. Per function: any compile, verify, link or size failure in `BatchCompiler` replaces the function with a stub that dispatches to vanilla (`Function {} falls back to vanilla: {}`, `Failed to compile function {}`).
4. At runtime: a throwing compiled function is disabled (`entry.function = null`) and runs through vanilla from then on (`Compiled function {} threw; running it through vanilla from now on.`).

## Pipeline
Commands -> `CommandCompiler` (high-level IL) -> `Optimizer` -> emitter (low-level `BytecodeInstruction`) -> `BytecodeOptimizer` -> `JavaStackCompiler` -> batch classes (`BatchClassPlanner`, max 800 methods per class) -> `DispatcherGenerator` (one `CompiledFunction` per id).

## Runtime
- `ServerFunctionManagerMixin.run` and `CallFunctionMixin.execute` switch to the compiled entry when the loader is `READY_ON`, on the server thread, and below the recursion limit. Otherwise vanilla runs.
- The result is a long: the high 32 bits hold 0 = success, 1 = failure, 2 = no return.
- `maxCommandChainLength` is not counted in compiled code (documented behaviour).

## Macros
- `$` lines are parsed with macro support; `$(x)` becomes `LOAD_MACRO`. A macro function gets a typed body plus a trampoline that validates arguments and falls back to vanilla on a missing or invalid argument.
- Lines that cannot be parsed even as templates are built at runtime and run through vanilla.

## Diagnostics
- `/mcfc state | show_diagnostics | is_compiled <fn> | recompile | reload_config | debug on|off|flush|reset`.
- `/mcfc debug` writes `logs/mcfc-debug.log` with per-function call counts and a compiled/vanilla/error label.
- After each load one INFO line `[Summary] N function(s) found, N parsed, N compiled, N registered; ...` is logged.

## Tests
- `tests/**/*.mctest` (`@setup`, `@function`, `@validate`) are turned into game tests by `annotation-processor/.../tests/TestsGenerator.kt`. `mc-26-1-fabric/src/gametest/.../TestInfo` runs each test compiled and with vanilla and fails when the server state differs.
- Run with `./gradlew build` (includes `:mc-26-1-fabric:runGameTest`). Baseline on 2026-10-26: 60 of 60 required tests pass.
