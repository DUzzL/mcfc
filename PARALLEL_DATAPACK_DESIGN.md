# Parallel datapack execution — architecture (MFC)

Goal: parallelize the per-tick work of compiled datapack functions so datapacks stop
contributing to server-thread lag, WITHOUT corrupting shared world state. Designed as
one framework so adding more parallel-safe commands later is additive, not breaking.

## Core model: batched read-fan-out + deferred (buffered) mutation + serial apply

Parallelism is limited by **dependencies**, not work volume. The only datapack work
with many independent units is the **fan-out** of multi-entity selectors
(`as @a[...]`, `as @e[...]`). So:

1. **Read phase (parallel, batched):** split the matched selector list into
   `poolSize` batches (NOT one task per element — batch to amortize dispatch, exactly
   like async's `overwriteEntityTicking`). Each batch runs its iterations' bodies, but:
   - all READS hit a **frozen per-tick snapshot** (entities, scoreboard, tags, blocks)
     — design target only: no snapshot exists today, reads go to live state;
   - all WRITES are recorded into a **thread-local mutation buffer** (fire-and-forget),
     NOT applied to the world.
2. **Apply phase (serial, at the join barrier):** merge buffers and apply mutations on
   the main thread in a **deterministic order** (by entity order / UUID).

Cross-iteration reads see the snapshot, i.e. they do NOT see another iteration's
same-tick writes → a write becomes visible **next tick**. This 1-tick latency is the
price of parallelism and is acceptable for the vast majority of advancement datapacks
(the checks still fire, just one tick later). Functions that need exact same-tick
read-after-write on shared state are excluded (see Safety).

## Components

### 1. Buffer-aware mutation helpers (minimal codegen change)
The compiled body already calls runtime helpers for mutations
(`ExtensionFunctions.setScoreSafe`, advancement grant, tag add/remove, ...). Make these
**buffer-aware via a ThreadLocal mode**: if the current thread is a buffering worker,
append `(target, op, value)` to its buffer; else write through as today. Reads
(`getScore`, `getEntitiesInside`, ...) read the snapshot. **No per-command codegen
change needed** — the same bytecode works in both modes. (Mirrors the lesson from
`feedback_threadlocal_set_remove`: one ThreadLocal holding a mutable frame, set on the
worker at batch entry, cleared at exit — never per-command.)

### 2. Parallel fan-out codegen (the one codegen change)
In `CommandCompiler` `CommandExecuteAs` / `CompilationContext.forSelector`: when the
selector matches >1 entity AND the body is classified parallel-safe, emit a
**batched parallel dispatch** (gather list → `poolSize` chunks → `ParallelProcessor`
pool, buffering mode on → `pumpUntilDone` → serial apply) instead of the serial loop.
Reuse async's `ParallelProcessor.executor` (shared pool, no second pool). Otherwise emit
the existing serial loop unchanged.

### 3. Per-tick snapshots
- **Entities:** not implemented (the old `TickEntities` snapshot/buckets/box memo were
  removed; selectors now read the live `EntityLookup` type index and section storage).
- **Scoreboard:** snapshot the read side once per tick (copy-on-first-parallel-read, or
  a concurrent read-only view). Reads during the read phase use it; the real scoreboard
  is only touched in the apply phase.
- **Blocks / tags:** safe to read directly during the read phase **because no thread
  writes the world then** (writes are buffered). Holds only if (2) guarantees the read
  phase runs in a window with no concurrent world mutation.

### 4. Compile-time safety classifier
Walk the body's IL (transitively through `run function`) and mark the `as` parallel-safe
iff every command is either a pure read or a **bufferable** mutation, with no
**same-tick read-after-write on shared (non-`@s`) state**, and no recursion into unsafe
commands. Conservative default: unknown → serial. The classification result is what
gates (2). This is the piece that must be correct — a false "safe" = corruption.

### Bufferable vs serial-fallback
- **Bufferable:** `scoreboard players add/set/reset` (per-`@s` holder, or commutative
  `add` to shared), `tag @s add/remove`, `advancement grant/revoke @s`. Apply in
  deterministic order; `add` is commutative (sum), `set` needs order.
- **Serial fallback (never parallelized):** `summon`, `setblock`, `fill`, `clone`,
  `kill`, `data modify` of shared targets, `function` recursion that loops back,
  anything that reads world state another iteration writes the same tick.

## Correctness & rollout
- **Config flag `enableParallelDatapacks` (default OFF).** Ship dark; enable after
  verification. Lets us A/B and never risk live survival data by default.
- **Verification gate (must pass before recommending ON):** the `parallel == serial`
  harness — run each candidate function both ways under identical state, assert every
  scoreboard/advancement/tag result is identical (cow-count style, but over the real
  hot functions). Then 50-bot load + JFR before/after, + jstack (no deadlock).
- **Deterministic apply order** so results are reproducible run-to-run.
- **Per-function opt-out** for packs that rely on exact same-tick ordering.

## Part B — concrete implementation plan (entry points found)

Status: Part A DONE (gated runtime: `MUTATION_BUFFER`, `bufferOrRun`, `parallelFanout`;
`setScoreSafe` buffer-aware). No read snapshots/prewarm exist (removed). Part B =
4 pieces, ALL needed together for safety (the fan-out is unsafe without 1+2):

1. **Classifier** (common, new pass after `FunctionCodeGenerator.parseFunctions` which
   returns `Map<DataLocation,List<CommandParams>>`): compute `Set<DataLocation> parallelSafe`.
   Walk each function's commands (records generated in
   `common/.../commands/params/` — ~50 types: `CommandExecuteAs/At/Run/If*/Unless*`,
   `CommandScoreboardPlayersAdd/Set`, `CommandFunction`, `CommandKill`, `CommandSummon`,
   `CommandData*`, `CommandTag*`, `CommandAdvancement*`, ...). CONSERVATIVE whitelist:
   safe = reads (`if/unless` conditions), `execute` modifiers wrapping safe bodies, and
   ONLY the buffered mutations (currently just scoreboard set/add). ANYTHING else
   (tag/advancement/summon/setblock/data/`run function`/vanilla-fallback/unknown) → mark
   the whole function UNSAFE. (`run function` → unsafe unless you do transitive fixpoint;
   start by rejecting it.) Recurse into modifier bodies.
2. **More buffer-aware mutations** (mc-26-1-fabric): only `setScoreSafe` done. Until
   advancement/tag/etc. are buffer-aware, the classifier MUST reject functions using them
   (it already does — they're not in the safe whitelist). So v1 = score-only functions.
3. **Runtime** (mc-26-1-fabric `ExtensionFunctions`): `parallelRunFunctionFanout(level,
   entityList, baseStack, fnId, atSelf)` → `parallelFanout(list, e -> { var s =
   baseStack.withEntity(e); if(atSelf) s = s.withLevel((ServerLevel)e.level())
   .withPosition(e.position()).withRotation(...); FunctionCompilerMain.functionClassLoader
   .runFunction(fnId, s, null); })`. Guard: `functionClassLoader != null && isUseReady()
   && hasFunctionLoaded(fnId)`; else serial fallback. The function's `setScoreSafe` calls
   buffer automatically (worker thread has MUTATION_BUFFER set).
4. **Codegen** (`CommandCompiler` `case CommandExecuteAs`): if
   `GlobalConfig.enableParallelDatapacks` AND body is `[CommandExecuteAt(@s)] →
   CommandExecuteRun → CommandFunction([fn])` (single fn) AND `fn ∈ parallelSafe` AND
   the selector matches many → instead of `forSelector` loop, take `getSelectorList(...)`
   and emit a call to `parallelRunFunctionFanout` (add a DSL instr
   `PARALLEL_RUN_FUNCTION` like `GET_ENTITIES_OF_TYPE` was added — that path works).
   Else: existing serial loop unchanged.

**Verify (gate before recommending the flag ON):** whitelist a score-only test function,
run it `as @e[...] run function` parallel vs a serial copy under identical state, assert
every scoreboard value identical (cow-count-style harness). Then 50-bot load + jstack (no
deadlock, parallel workers visible). Each build cycle: rebuild → USER restarts server →
RCON verify. Flag stays OFF until parallel==serial confirmed.

## Honest scope note
The dump under 50 bots showed the server-thread hot path in `LevelTicks`
(block/fluid tick scheduling) + `async$overwriteEntityTicking` chunk checks — **NOT in
datapacks** (after the read-side optimizations they're already cheap). Measure datapack
mutation time (JFR) before investing: parallelizing mutations only pays if there is
meaningful serial datapack-mutation time to reclaim. Reads are already parallel/cached.
