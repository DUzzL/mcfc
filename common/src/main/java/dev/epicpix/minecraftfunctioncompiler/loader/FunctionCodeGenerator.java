package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParser;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.commands.CommandSourceMap;
import dev.epicpix.minecraftfunctioncompiler.commands.ParsedCommandList;
import dev.epicpix.minecraftfunctioncompiler.commands.StringCommandReader;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroType;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFallbackVanilla;
import dev.epicpix.minecraftfunctioncompiler.compiler.ChainOutliner;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticCollector;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticResult;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.FunctionResolver;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.OutlinedCall;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeOptimizer;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.LengauerTarjan;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.UNSPECIFIED_COMMAND_ERROR;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callDouble_parseDouble;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callFloat_parseFloat;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callInteger_parseInt;

public class FunctionCodeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    /**
     * Joins continuation lines and drops blank/comment lines exactly like vanilla's CommandFunction.fromLines.
     * A function whose last line is a continuation is rejected by vanilla ("Line continuation at end of file");
     * such a function is skipped here with a diagnostic instead of failing the whole compile.
     */
    public static HashMap<DataLocation, List<String>> loadFunctionSources(Map<DataLocation, List<String>> sourceFiles, DiagnosticCollector diagnostics) {
        HashMap<DataLocation, List<String>> readFunctions = new HashMap<>(sourceFiles.size());
        outer:
        for(var entry : sourceFiles.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            ArrayList<String> commands = new ArrayList<>(values.size());
            for(int i = 0; i < values.size(); i++) {
                String line = values.get(i).trim();
                if(endsWithContinuation(line)) {
                    StringBuilder value = new StringBuilder(line);
                    do {
                        if(++i == values.size()) {
                            diagnostics.addDiagnostic(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, "Line continuation at end of file", null));
                            continue outer;
                        }
                        value.deleteCharAt(value.length() - 1);
                        value.append(values.get(i).trim());
                    } while(endsWithContinuation(value));
                    line = value.toString();
                }
                if(line.isEmpty() || line.charAt(0) == '#') continue;
                commands.add(line);
            }
            readFunctions.put(key, commands);
        }
        return readFunctions;
    }

    private static boolean endsWithContinuation(CharSequence line) {
        int length = line.length();
        return length > 0 && line.charAt(length - 1) == '\\';
    }

    public static HashMap<DataLocation, List<CommandParams>> parseFunctions(HashMap<DataLocation, List<String>> functionSources, IArgumentLookup lookup, DiagnosticCollector diagnostics, java.util.function.BooleanSupplier cancelled) {
        // Pre-allocate a per-file array so each command can be written to its final
        // index without cross-thread contention. Result order within a function must
        // match input order (mcfunction semantics are sequential).
        HashMap<DataLocation, CommandParams[]> resultArrays = new HashMap<>(functionSources.size());
        HashMap<DataLocation, CommandSourceMap[]> sourceArrays = new HashMap<>(functionSources.size());
        for(var entry : functionSources.entrySet()) {
            resultArrays.put(entry.getKey(), new CommandParams[entry.getValue().size()]);
            sourceArrays.put(entry.getKey(), new CommandSourceMap[entry.getValue().size()]);
        }

        var totalFailures = new java.util.concurrent.atomic.AtomicInteger(0);
        var parseFailures = new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>>();

        try(var executor = LowPriorityCompileExecutor.newScope()) {
            for(var entry : functionSources.entrySet()) {
                final var key = entry.getKey();
                final var values = entry.getValue();
                final var arr = resultArrays.get(key);
                final var srcArr = sourceArrays.get(key);
                // One task per function (L-14): per-line tasks cost more in scheduling than parsing.
                executor.execute(() -> {
                    if(cancelled.getAsBoolean()) return;
                    for(int idx = 0; idx < values.size(); idx++) {
                        final String value = values.get(idx);
                        boolean macroLine = value.startsWith("$");
                        String line = macroLine ? value.substring(1) : value;
                        var reader = new StringCommandReader(line, key, macroLine);
                        DiagnosticResult<CommandParams> res;
                        try {
                            res = CommandParser.parseCommand(reader, lookup);
                        } catch(DiagnosticException e) {
                            res = DiagnosticResult.error(e.toDiagnostic());
                        } catch(Throwable e) {
                            // A parser bug must never leave a null command behind: fall back to vanilla (PAR-19).
                            res = DiagnosticResult.error(Diagnostic.parseError(UNSPECIFIED_COMMAND_ERROR, key, value, String.valueOf(e), e));
                        }
                        srcArr[idx] = reader.getSources();
                        if(res.isError()) {
                            var diag = res.getError();
                            assert diag != null;
                            diagnostics.addDiagnosticSafe(diag);
                            arr[idx] = new CommandFallbackVanilla(line);
                            totalFailures.incrementAndGet();
                            parseFailures
                                .computeIfAbsent(diag.message(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                                .computeIfAbsent(key.namespace(), k -> new java.util.concurrent.atomic.AtomicInteger(0))
                                .incrementAndGet();
                        }else {
                            arr[idx] = Objects.requireNonNull(res.getResult());
                        }
                    }
                });
            }
        } // ExecutorService.close() blocks until every task finishes

        HashMap<DataLocation, List<CommandParams>> definedFunctions = new HashMap<>(resultArrays.size());
        for(var entry : resultArrays.entrySet()) {
            definedFunctions.put(entry.getKey(), new ParsedCommandList(entry.getValue(), sourceArrays.get(entry.getKey())));
        }

        int totalFailuresCount = totalFailures.get();
        if(totalFailuresCount > 0) {
            var summary = new StringBuilder();
            summary.append("Partially compiled ").append(totalFailuresCount).append(" command(s) with vanilla fallback. Reasons:");
            var sortedEntries = new ArrayList<>(parseFailures.entrySet());
            sortedEntries.sort((a, b) -> {
                int countA = a.getValue().values().stream().mapToInt(java.util.concurrent.atomic.AtomicInteger::get).sum();
                int countB = b.getValue().values().stream().mapToInt(java.util.concurrent.atomic.AtomicInteger::get).sum();
                return Integer.compare(countB, countA);
            });
            for(var reasonEntry : sortedEntries) {
                int reasonTotal = reasonEntry.getValue().values().stream().mapToInt(java.util.concurrent.atomic.AtomicInteger::get).sum();
                summary.append("\n  ").append(reasonEntry.getKey()).append(": ").append(reasonTotal).append(" command(s)");
                var namespaces = new ArrayList<>(reasonEntry.getValue().entrySet());
                namespaces.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
                summary.append(" (");
                for(int i = 0; i < namespaces.size(); i++) {
                    if(i > 0) summary.append(", ");
                    summary.append(namespaces.get(i).getKey()).append(": ").append(namespaces.get(i).getValue().get());
                }
                summary.append(")");
            }
            LOGGER.warn("{}", summary);
        }
        return definedFunctions;
    }

    public interface NbtTagBytecodeStringGenerator {
        BytecodeValue nbtTagToBytecodeString(EmitterContext context, String key, @Nullable BytecodeLabel errorLabel);
    }

    private record MacroInput(String source, MacroType<?> type) {}

    private static void writeMacroInstanceCreation(NbtTagBytecodeStringGenerator nbtStringGenerator, ClassDesc className, String instancedMethodName, BytecodeLabel errorLabel, EmitterContext context, List<Instruction> macroInstructions, List<MacroInput> macroInputs, ClassDesc commandSourceStackType) {
        context.codeWriter().conditionalJump(JumpCondition.EQ, context.getStatic("FunctionCompound"), new BytecodeValue.NullValue(), errorLabel);

        HashMap<String, HashSet<MacroValidator>> generatedValidators = new HashMap<>();
        HashMap<String, BytecodeValue> stringifiedValues = new HashMap<>();

        for(var macroInstr : macroInstructions) {
            var macros = Objects.requireNonNull(macroInstr.<MacroTemplate<String>>get("macro")).extractMacros();
            for(var macro : macros) {
                var generated = generatedValidators.computeIfAbsent(macro.source(), k -> new HashSet<>());
                if(generated.containsAll(macro.validators())) {
                    continue;
                }
                var value = stringifiedValues.computeIfAbsent(macro.source(), key -> nbtStringGenerator.nbtTagToBytecodeString(context, key, errorLabel));
                for(var validator : macro.validators()) {
                    if(generated.add(validator)) {
                        validator.validateGenerated(value, context.codeWriter(), context.fieldStorage(), errorLabel);
                    }
                }
            }
        }

        List<ClassDesc> targetArgs = new ArrayList<>();
        targetArgs.add(commandSourceStackType);
        List<BytecodeValue> arguments = new ArrayList<>();
        arguments.add(context.getStatic("CommandSourceStack"));
        for(var macro : macroInputs) {
            var arg = stringifiedValues.computeIfAbsent(macro.source(), key -> nbtStringGenerator.nbtTagToBytecodeString(context, key, errorLabel));
            if(macro.type == MacroType.STRING_MACRO_TYPE) {
                arguments.add(arg);
            }else if(macro.type == MacroType.INT_MACRO_TYPE) {
                arguments.add(callInteger_parseInt(context.codeWriter(), arg));
            }else if(macro.type == MacroType.FLOAT_MACRO_TYPE) {
                arguments.add(callFloat_parseFloat(context.codeWriter(), arg));
            }else if(macro.type == MacroType.DOUBLE_MACRO_TYPE) {
                arguments.add(callDouble_parseDouble(context.codeWriter(), arg));
            }
            targetArgs.add(macro.type.getType().describeConstable().orElseThrow());
        }

        var target = new InstructionTarget.FunctionTarget(className, instancedMethodName,  MethodTypeDesc.of(ConstantDescs.CD_long, targetArgs), true, false, false, true);
        context.codeWriter().returnValue(context.codeWriter().writeTarget(target, arguments));
    }

    // ── Per-function compilation into size-bounded methods ─────────────────────
    //
    // HotSpot never JIT-compiles a method whose bytecode exceeds 8000 bytes
    // (-XX:HugeMethodLimit), and the class-file format rejects code over 65535 bytes.
    // A function is therefore compiled as a sequence of command CHUNKS: each chunk is
    // a contiguous command range compiled in its own CompilationContext (commands are
    // independent; they share only the base command stack, which every chunk reloads
    // from its parameter), optimized on its own and emitted as its own static method.
    // A dispatcher calls the chunks in order and stops at the first chunk that
    // returned (`return` inside a chunk yields a non-sentinel value).

    /** Planning budget per body chunk in {@link BytecodeInstruction#cost()} units. cost()
     *  overestimates the encoded length by ~1.5x on real packs, so chunks land at ~6 KB of
     *  real code: under the 8000-byte JIT limit with room for C2, yet as few chunks as
     *  possible (every chunk boundary repeats lookups the optimizer would otherwise share). */
    public static final int TARGET_METHOD_COST = 9000;
    /** A chunk whose estimated cost exceeds this is re-split before it is built. */
    static final int MAX_METHOD_COST = 11000;
    /** Hard cap on the real Code-attribute length of a chunk method, checked after the
     *  class is built (cost() is an estimate: ldc_w, invokeinterface, wide locals). */
    public static final int MAX_METHOD_CODE_LENGTH = 7900;
    /** Target real length when a built chunk has to be re-split. */
    public static final int TARGET_METHOD_CODE_LENGTH = 5000;
    /** Functions longer than this are first compiled in pieces of at most this many
     *  commands, bounding optimizer time, then refined by cost. */
    static final int INITIAL_CHUNK_COMMANDS = 128;
    /** Chunk calls per dispatcher method (~19 bytes each, so ~5 KB per dispatcher). */
    static final int DISPATCH_GROUP_SIZE = 256;
    private static final long NO_RETURN_SENTINEL = 0x2L << 32;

    /**
     * Shared, thread-safe inputs of one compilation.
     *
     * @param compiledFunctions functions that are called through {@code invokestatic} (all planned ones)
     * @param lowLevelFunctions debug-only sink of emitted bytecode per method, or null
     * @param debugCompiled     debug-only sink of unoptimized IL per chunk, or null
     * @param debugOptimized    debug-only sink of optimized IL per chunk, or null
     */
    public record Environment(
        CompilationParams params, FunctionResolver resolver, Set<DataLocation> compiledFunctions,
        java.util.concurrent.atomic.LongAdder preOptInstructions, java.util.concurrent.atomic.LongAdder postOptInstructions,
        @Nullable Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        @Nullable Map<DataLocation, List<Instruction>> debugCompiled,
        @Nullable Map<DataLocation, List<Instruction>> debugOptimized
    ) {
        private ClassDesc nbtType() {
            return params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);
        }

        /** Descriptor of every function entry point: {@code long (CommandSourceStack, CompoundTag)}. */
        public MethodTypeDesc entryType() {
            return MethodTypeDesc.of(ConstantDescs.CD_long, params.commandSourceStackType(), nbtType());
        }
    }

    /** A command range compiled as one method body, plus the execute-chain tails outlined out of it
     *  (only single-command chunks have outlines; see {@link #outlineChunk}). */
    private record Chunk(FunctionMethods.CommandRange range, List<Instruction> unoptimized, List<Instruction> instructions, List<Outline> outlines) {
        /** IL of every method of this chunk: its body, then its outlines. */
        List<List<Instruction>> allInstructions() {
            var all = new ArrayList<List<Instruction>>(1 + outlines.size());
            all.add(instructions);
            for(var outline : outlines) all.add(outline.instructions());
            return all;
        }
    }

    /** One outlined execute-chain tail: the method {@code call} targets, with its emitted {@code body}. */
    private record Outline(OutlinedCall call, List<Instruction> unoptimized, List<Instruction> instructions, List<BytecodeInstruction> body) {}

    /**
     * Compiles one function into all of its methods.
     *
     * @param ranges command ranges to use as the initial chunks, or null to plan them;
     *               used to re-split a chunk whose built code turned out too long
     * @throws DiagnosticException or any other exception if the function cannot be
     *         compiled; the caller replaces it with {@link #emitStub}
     */
    public static FunctionMethods compileFunction(
        Environment env, DataLocation key, List<CommandParams> commands,
        BatchClassPlanner.PlacedFunction placement, ClassDesc ownerClass,
        @Nullable List<FunctionMethods.CommandRange> ranges
    ) {
        if(ranges == null) ranges = splitRange(new FunctionMethods.CommandRange(0, commands.size()),
            Math.ceilDiv(commands.size(), INITIAL_CHUNK_COMMANDS));
        var chunks = new ArrayList<Chunk>(ranges.size());
        for(var range : ranges) chunks.add(compileChunk(env, key, commands, range));

        var macroInputs = collectMacroInputs(chunks);
        String base = placement.baseName();
        boolean hasMacros = !macroInputs.isEmpty();
        String bodyName = hasMacros ? BatchClassPlanner.instancedMethodName(base) : base;
        var bodyType = hasMacros ? macroBodyType(env, macroInputs) : env.entryType();
        var outlineTargets = new OutlineTargets(ownerClass, bodyName, bodyType, hasMacros ? macroInputs.stream().map(MacroInput::source).toList() : null);

        var bodies = new ArrayList<List<BytecodeInstruction>>(chunks.size());
        for(int i = 0; i < chunks.size(); ) {
            var chunk = chunks.get(i);
            var body = emitChunk(env, key, chunk.instructions(), macroInputs);
            int cost = 0;
            for(var instr : body) cost += instr.cost();
            if(cost > MAX_METHOD_COST && chunk.range().size() > 1) {
                int parts = Math.max(2, Math.ceilDiv(cost, TARGET_METHOD_COST));
                chunks.remove(i);
                var replacement = new ArrayList<Chunk>(parts);
                for(var range : splitRange(chunk.range(), parts)) replacement.add(compileChunk(env, key, commands, range));
                chunks.addAll(i, replacement);
                continue; // re-emit chunk i; bodies.size() == i still holds
            }
            if(chunk.range().size() == 1 && chunk.outlines().isEmpty() && cost > OUTLINE_PROBE_COST
                    && codeLength(ownerClass, bodyType, body) > OUTLINE_CODE_LENGTH) {
                chunks.set(i, outlineChunk(env, key, commands, chunk.range(), outlineTargets, macroInputs));
                continue; // re-emit chunk i with its tails outlined
            }
            bodies.add(body);
            i++;
        }
        // Splitting a range or outlining a command never changes which macros the function reads,
        // so the parameter list every body was emitted against is still the function's.
        if(!new HashSet<>(collectMacroInputs(chunks)).equals(new HashSet<>(macroInputs))) throw new IllegalStateException("macro inputs changed while splitting " + key);

        var finalRanges = new ArrayList<FunctionMethods.CommandRange>(chunks.size());
        for(int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            finalRanges.add(chunk.range());
            var debugKey = chunks.size() == 1 ? key : new DataLocation(key.namespace(), key.path() + "$chunk" + i);
            addInstructionStats(env, debugKey, chunk.unoptimized(), chunk.instructions());
            for(var outline : chunk.outlines()) {
                addInstructionStats(env, new DataLocation(key.namespace(), key.path() + "$" + outline.call().name()), outline.unoptimized(), outline.instructions());
            }
        }

        var methods = new ArrayList<MethodEmission>(bodies.size() + 2);
        if(bodies.size() == 1) {
            addMethod(env, methods, base, bodyName, bodies.getFirst(), bodyType, 0);
        }else {
            var chunkNames = new ArrayList<String>(bodies.size());
            for(int i = 0; i < bodies.size(); i++) {
                var chunkName = BatchClassPlanner.chunkMethodName(bodyName, i);
                addMethod(env, methods, base, chunkName, bodies.get(i), bodyType, i);
                chunkNames.add(chunkName);
            }
            emitDispatchTree(env, methods, base, ownerClass, bodyName, bodyType, chunkNames);
        }
        for(int i = 0; i < chunks.size(); i++) {
            for(var outline : chunks.get(i).outlines()) addMethod(env, methods, base, outline.call().name(), outline.body(), bodyType, i);
        }
        if(hasMacros) {
            var macroInstructions = new ArrayList<Instruction>();
            for(var chunk : chunks) {
                for(var instructions : chunk.allInstructions()) macroInstructions.addAll(Instruction.findTyped(instructions, InstructionTypes.LOAD_MACRO));
            }
            addMethod(env, methods, base, base, emitMacroTrampoline(env, key, ownerClass, bodyName, macroInstructions, macroInputs), env.entryType(), MethodEmission.NO_CHUNK);
        }
        return new FunctionMethods(key, hasMacros, false, List.copyOf(methods), List.copyOf(finalRanges));
    }

    private static void addInstructionStats(Environment env, DataLocation debugKey, List<Instruction> unoptimized, List<Instruction> optimized) {
        env.preOptInstructions().add(unoptimized.size());
        env.postOptInstructions().add(optimized.size());
        if(env.debugCompiled() != null) env.debugCompiled().put(debugKey, Instructions.addStaticRequirements(unoptimized));
        if(env.debugOptimized() != null) env.debugOptimized().put(debugKey, optimized);
    }

    // ── Outlining of oversized execute chains ──────────────────────────────────
    //
    // One command can by itself exceed the JIT limit (e.g. `execute if entity … if entity …`
    // with dozens of clauses). Such a command is cut at chain nodes the compiler reports as
    // safe (ChainOutliner): each cut node's tail becomes a sibling method with the body's own
    // descriptor, called with the chain's current source materialized as a CommandSourceStack,
    // exactly as a forked chain hands sources to its body. Segments that are still too long
    // are cut again in the middle until every method fits or no cut point is left.

    /** Single-command chunks whose estimated cost exceeds this get their real code length measured. */
    static final int OUTLINE_PROBE_COST = 3500;
    /** Real code length a single command's method may reach before its execute chain is outlined;
     *  below {@link #MAX_METHOD_CODE_LENGTH} because the batch's larger constant pool turns some
     *  {@code ldc} into {@code ldc_w}. */
    static final int OUTLINE_CODE_LENGTH = 7000;

    /** Where a function's outlined methods go; {@link #next} names them {@code <body>$o<n>}. */
    private static final class OutlineTargets {
        private final ClassDesc owner;
        private final String bodyName;
        private final MethodTypeDesc type;
        private final @Nullable List<String> macroSources;
        private int count;

        OutlineTargets(ClassDesc owner, String bodyName, MethodTypeDesc type, @Nullable List<String> macroSources) {
            this.owner = owner;
            this.bodyName = bodyName;
            this.type = type;
            this.macroSources = macroSources;
        }

        OutlinedCall next() {
            return new OutlinedCall(owner, bodyName + "$o" + count++, type, macroSources);
        }
    }

    /** A compiled chain segment: the code from {@code root} up to (excluding) the next cut. */
    private record Segment(List<Instruction> unoptimized, List<Instruction> instructions, List<BytecodeInstruction> body, int length) {}

    /**
     * Compiles the single command at {@code range} with its execute chain cut into methods of at most
     * {@link #OUTLINE_CODE_LENGTH} bytes where the chain allows it. Cut points are chain nodes in
     * chain order; a segment over the limit is cut at its middle cut point until none is left.
     */
    private static Chunk outlineChunk(Environment env, DataLocation key, List<CommandParams> commands, FunctionMethods.CommandRange range, OutlineTargets targets, List<MacroInput> macroInputs) {
        var command = commands.get(range.from());
        var sourceMap = commands instanceof ParsedCommandList parsed ? parsed.sourceOf(range.from()) : null;
        var cutPoints = new ArrayList<CommandParams>();
        compileTail(key, command, sourceMap, tail -> {
            if(tail != command && cutPoints.stream().noneMatch(p -> p == tail)) cutPoints.add(tail);
            return null;
        });

        // Segment i starts at cut point i (-1: the command itself) and ends before the next cut.
        var cuts = new java.util.TreeSet<Integer>();
        var calls = new HashMap<Integer, OutlinedCall>();
        var segments = new HashMap<Long, Segment>();
        boolean changed = true;
        while(changed) {
            changed = false;
            int from = -1;
            for(Integer next = cuts.isEmpty() ? null : cuts.first(); ; next = cuts.higher(from)) {
                int to = next == null ? cutPoints.size() : next;
                var segment = segment(env, key, command, sourceMap, cutPoints, calls, segments, from, to, targets, macroInputs);
                if(segment.length() > OUTLINE_CODE_LENGTH && to - from >= 2) {
                    int cut = (from + to + 1) >>> 1;
                    cuts.add(cut);
                    calls.computeIfAbsent(cut, _ -> targets.next());
                    changed = true;
                }
                if(next == null) break;
                from = next;
            }
        }

        Segment main = null;
        var outlines = new ArrayList<Outline>(cuts.size());
        int from = -1;
        for(Integer next = cuts.isEmpty() ? null : cuts.first(); ; next = cuts.higher(from)) {
            int to = next == null ? cutPoints.size() : next;
            var segment = segment(env, key, command, sourceMap, cutPoints, calls, segments, from, to, targets, macroInputs);
            if(segment.length() > OUTLINE_CODE_LENGTH) {
                LOGGER.warn("Command {} of {} still compiles to a {}-byte method after outlining (no cut point left)", range.from(), key, segment.length());
            }
            if(from < 0) main = segment;
            else outlines.add(new Outline(calls.get(from), segment.unoptimized(), segment.instructions(), segment.body()));
            if(next == null) break;
            from = next;
        }
        Objects.requireNonNull(main);
        LOGGER.debug("Outlined command {} of {} into {} extra method(s); remaining body {} bytes", range.from(), key, outlines.size(), main.length());
        return new Chunk(range, main.unoptimized(), main.instructions(), List.copyOf(outlines));
    }

    /** Segment from cut point {@code from} (-1: {@code command}) up to cut point {@code to} (exclusive;
     *  {@code cutPoints.size()}: to the end of the chain), compiled once per (from, to). */
    private static Segment segment(Environment env, DataLocation key, CommandParams command, @Nullable CommandSourceMap sourceMap,
                                   List<CommandParams> cutPoints, Map<Integer, OutlinedCall> calls, Map<Long, Segment> cache,
                                   int from, int to, OutlineTargets targets, List<MacroInput> macroInputs) {
        return cache.computeIfAbsent(((long) from << 32) | (to & 0xFFFFFFFFL), _ -> {
            var root = from < 0 ? command : cutPoints.get(from);
            var cutAt = to < cutPoints.size() ? cutPoints.get(to) : null;
            var cutTaken = new boolean[1];
            var compiled = compileTail(key, root, sourceMap, tail -> {
                if(tail != cutAt) return null;
                cutTaken[0] = true;
                return calls.get(to);
            });
            // The cut points were discovered on the whole command; a segment compiled from one of them
            // reaches the next one in the same state (same fork decision, no result callback).
            if(cutAt != null && !cutTaken[0]) throw new IllegalStateException("outline cut point not reached in " + key);
            var optimized = Instructions.addStaticRequirements(Optimizer.optimize(compiled, new LengauerTarjan()));
            var body = emitChunk(env, key, optimized, macroInputs);
            return new Segment(compiled, optimized, body, codeLength(targets.owner, targets.type, body));
        });
    }

    /** Compiles {@code root} (a whole command or a chain tail) as one top-level command. */
    private static List<Instruction> compileTail(DataLocation key, CommandParams root, @Nullable CommandSourceMap sourceMap, ChainOutliner outliner) {
        List<CommandParams> single = sourceMap != null
            ? new ParsedCommandList(new CommandParams[]{root}, new CommandSourceMap[]{sourceMap})
            : List.of(root);
        return CommandCompiler.compile(key, single, outliner);
    }

    /** Real Code-attribute length of {@code body} as a method of {@code ownerClass}. */
    private static int codeLength(ClassDesc ownerClass, MethodTypeDesc type, List<BytecodeInstruction> body) {
        var bytes = ClassGenerator.createClass(ownerClass, List.of(MethodEmission.of("m", body, type, MethodEmission.NO_CHUNK)));
        return ClassGenerator.codeLength(ClassGenerator.parse(bytes).methods().getFirst());
    }

    /**
     * Entry point for a function that could not be compiled: same name and descriptor as
     * the real entry point, so call sites already emitted against it stay valid, with a
     * body that runs the function through vanilla (the path used for any uncompiled callee).
     */
    public static FunctionMethods emitStub(Environment env, DataLocation key, BatchClassPlanner.PlacedFunction placement) {
        var writer = new CodeWriter();
        var call = vanillaCall(env, key, writer);
        call.context().emitForEach();
        writer.returnValue(call.result());
        var methods = new ArrayList<MethodEmission>(1);
        addMethod(env, methods, placement.baseName(), placement.baseName(), BytecodeOptimizer.optimizeInstructions(writer.getInstructions()), env.entryType(), MethodEmission.NO_CHUNK);
        return new FunctionMethods(key, false, true, List.copyOf(methods), List.of());
    }

    /** Splits {@code range} into {@code parts} contiguous ranges of near-equal command count
     *  (at least one range; never more ranges than commands, except for an empty range). */
    public static List<FunctionMethods.CommandRange> splitRange(FunctionMethods.CommandRange range, int parts) {
        parts = Math.clamp(parts, 1, Math.max(1, range.size()));
        var result = new ArrayList<FunctionMethods.CommandRange>(parts);
        for(int p = 0; p < parts; p++) {
            int from = range.from() + (int) ((long) range.size() * p / parts);
            int to = range.from() + (int) ((long) range.size() * (p + 1) / parts);
            result.add(new FunctionMethods.CommandRange(from, to));
        }
        return result;
    }

    private static Chunk compileChunk(Environment env, DataLocation key, List<CommandParams> commands, FunctionMethods.CommandRange range) {
        var compiled = CommandCompiler.compile(key, commands.subList(range.from(), range.to()));
        var optimized = Instructions.addStaticRequirements(Optimizer.optimize(compiled, new LengauerTarjan()));
        return new Chunk(range, compiled, optimized, List.of());
    }

    private static List<MacroInput> collectMacroInputs(List<Chunk> chunks) {
        var macroInputs = new ArrayList<MacroInput>();
        for(var chunk : chunks) {
            for(var instructions : chunk.allInstructions()) {
                for(var macroInstr : Instruction.findTyped(instructions, InstructionTypes.LOAD_MACRO)) {
                    var template = Objects.requireNonNull(macroInstr.<MacroTemplate<?>>get("macro"));
                    for(var macro : template.extractMacros()) {
                        var macroInput = new MacroInput(macro.source(), template.macroType());
                        if(!macroInputs.contains(macroInput)) macroInputs.add(macroInput);
                    }
                }
            }
        }
        return macroInputs;
    }

    private static MethodTypeDesc macroBodyType(Environment env, List<MacroInput> macroInputs) {
        var paramTypes = new ArrayList<ClassDesc>(macroInputs.size() + 1);
        paramTypes.add(env.params().commandSourceStackType());
        for(var input : macroInputs) paramTypes.add(input.type().getType().describeConstable().orElseThrow());
        return MethodTypeDesc.of(ConstantDescs.CD_long, paramTypes);
    }

    /** Parameters of {@code type} as values, indexed by JVM local slot (long/double take two). */
    private static List<BytecodeValue> parameterValues(MethodTypeDesc type) {
        var values = new ArrayList<BytecodeValue>(type.parameterCount());
        int slot = 0;
        for(var param : type.parameterList()) {
            values.add(new BytecodeValue.Parameter(slot, param));
            slot += java.lang.classfile.TypeKind.from(param).slotSize();
        }
        return values;
    }

    /** Emits one chunk body; macro chunks take the function's typed macro values as parameters. */
    private static List<BytecodeInstruction> emitChunk(Environment env, DataLocation key, List<Instruction> instructions, List<MacroInput> macroInputs) {
        var writer = new CodeWriter();
        var css = writer.createRegister();
        writer.setValue(css, new BytecodeValue.Parameter(0, env.params().commandSourceStackType()));
        var staticArguments = new HashMap<String, BytecodeValue>();
        staticArguments.put("CommandSourceStack", css);
        HashMap<String, BytecodeRegister> macroLocations = null;
        if(macroInputs.isEmpty()) {
            var nbt = writer.createRegister();
            writer.setValue(nbt, new BytecodeValue.Parameter(1, env.nbtType()));
            staticArguments.put("FunctionCompound", nbt);
        }else {
            macroLocations = new HashMap<>();
            var params = parameterValues(macroBodyType(env, macroInputs));
            for(int i = 0; i < macroInputs.size(); i++) {
                var register = writer.createRegister();
                writer.setValue(register, params.get(i + 1));
                macroLocations.put(macroInputs.get(i).source(), register);
            }
        }
        new EmitterContext(key, env.params().codeFieldStorage(), writer, env.compiledFunctions(), staticArguments, macroLocations, instructions, env.resolver()).emitForEach();
        writer.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));
        return BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
    }

    private static void addMethod(Environment env, List<MethodEmission> methods, String base, String name, List<BytecodeInstruction> body, MethodTypeDesc type, int chunkIndex) {
        if(env.lowLevelFunctions() != null) env.lowLevelFunctions().put(base + "#" + name, body);
        methods.add(MethodEmission.of(name, body, type, chunkIndex));
    }

    /** Emits {@code name} calling {@code targets} in order, through a tree of group
     *  dispatchers when there are more targets than fit one small method. */
    private static void emitDispatchTree(Environment env, List<MethodEmission> methods, String base, ClassDesc ownerClass, String name, MethodTypeDesc type, List<String> targets) {
        if(targets.size() > DISPATCH_GROUP_SIZE) {
            var groups = new ArrayList<String>(Math.ceilDiv(targets.size(), DISPATCH_GROUP_SIZE));
            for(int from = 0; from < targets.size(); from += DISPATCH_GROUP_SIZE) {
                var groupName = BatchClassPlanner.groupMethodName(name, groups.size());
                emitDispatchTree(env, methods, base, ownerClass, groupName, type, targets.subList(from, Math.min(targets.size(), from + DISPATCH_GROUP_SIZE)));
                groups.add(groupName);
            }
            targets = groups;
        }
        var writer = new CodeWriter();
        var args = parameterValues(type);
        for(int i = 0; i < targets.size(); i++) {
            var target = new InstructionTarget.FunctionTarget(ownerClass, targets.get(i), type, true, false, false, true);
            var result = writer.writeTarget(target, args);
            if(i < targets.size() - 1) {
                // A chunk that executed `return` yields a non-sentinel value: stop here.
                var continueLabel = writer.createLabel();
                writer.conditionalJump(JumpCondition.EQ, result, new BytecodeValue.LongValue(NO_RETURN_SENTINEL), continueLabel);
                writer.returnValue(result);
                writer.label(continueLabel);
            }else {
                writer.returnValue(result);
            }
        }
        addMethod(env, methods, base, name, BytecodeOptimizer.optimizeInstructions(writer.getInstructions()), type, MethodEmission.NO_CHUNK);
    }

    private record VanillaCall(EmitterContext context, BytecodeRegister result) {}

    /** Sets up an entry-point method ({@code long (CommandSourceStack, CompoundTag)}) whose
     *  context holds one instruction: run {@code key} through vanilla with the method's
     *  stack and arguments. Emitting the context writes the call; its value is {@code result}. */
    private static VanillaCall vanillaCall(Environment env, DataLocation key, CodeWriter writer) {
        var css = writer.createRegister();
        writer.setValue(css, new BytecodeValue.Parameter(0, env.params().commandSourceStackType()));
        var nbt = writer.createRegister();
        writer.setValue(nbt, new BytecodeValue.Parameter(1, env.nbtType()));
        var returnValue = writer.createRegister();
        var instr = List.of(Instructions2.callFunctionsWithResult(LocationData.ofLocal(-3), List.of(key), LocationData.ofLocal(-1), LocationData.ofLocal(-2)));
        var context = new EmitterContext(key, env.params().codeFieldStorage(), writer, Collections.emptySet(),
            new HashMap<>(Map.of("CommandSourceStack", css, "FunctionCompound", nbt)), null, instr, env.resolver());
        context.setTransform(-1, css);
        context.setTransform(-2, nbt);
        context.setTransform(-3, returnValue);
        return new VanillaCall(context, returnValue);
    }

    /** Macro entry point: validates and converts every macro argument (vanilla rejects a
     *  call before running any line), then calls the typed body; on a missing or invalid
     *  argument it runs the function through vanilla, which reports the error. */
    private static List<BytecodeInstruction> emitMacroTrampoline(Environment env, DataLocation key, ClassDesc ownerClass, String bodyName, List<Instruction> macroInstructions, List<MacroInput> macroInputs) {
        var writer = new CodeWriter();
        var call = vanillaCall(env, key, writer);
        var errorLabel = writer.createLabel();
        writeMacroInstanceCreation(env.params().stringGenerator(), ownerClass, bodyName, errorLabel, call.context(), macroInstructions, macroInputs, env.params().commandSourceStackType());
        writer.label(errorLabel);
        call.context().emitForEach();
        writer.returnValue(call.result());
        return BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
    }

}
