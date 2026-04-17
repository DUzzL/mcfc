package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParser;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroType;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFallbackVanilla;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticCollector;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticResult;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.FunctionResolver;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
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
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
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

    public static HashMap<DataLocation, List<String>> loadFunctionSources(HashMap<DataLocation, List<String>> sourceFiles) {
        HashMap<DataLocation, List<String>> readFunctions = new HashMap<>();
        for(var entry : sourceFiles.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            ArrayList<String> commands = new ArrayList<>();
            for(int i = 0; i<values.size(); i++) {
                StringBuilder value = new StringBuilder(values.get(i).trim());
                while(value.toString().endsWith("\\")) {
                    String next = values.get(++i).trim();
                    value.deleteCharAt(value.length() - 1);
                    value.append(next);
                }
                if(value.isEmpty() || value.charAt(0) == '#') continue;
                commands.add(value.toString());
            }
            readFunctions.put(key, commands);
        }
        return readFunctions;
    }

    public static HashMap<DataLocation, List<CommandParams>> parseFunctions(HashMap<DataLocation, List<String>> functionSources, IArgumentLookup lookup, DiagnosticCollector diagnostics) {
        // Pre-allocate a per-file array so each command can be written to its final
        // index without cross-thread contention. Result order within a function must
        // match input order (mcfunction semantics are sequential).
        HashMap<DataLocation, CommandParams[]> resultArrays = new HashMap<>(functionSources.size());
        for(var entry : functionSources.entrySet()) {
            resultArrays.put(entry.getKey(), new CommandParams[entry.getValue().size()]);
        }

        var totalFailures = new java.util.concurrent.atomic.AtomicInteger(0);
        var parseFailures = new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>>();

        // Fan every individual command out to a virtual thread. The inner per-command
        // loop used to be sequential, which pinned one CPU core for minutes on
        // datapacks whose single .mcfunction file contains tens of thousands of
        // near-identical commands (e.g. a big "execute ... run particle" carpet).
        // CommandParser is stateless (each call builds its own StringCommandReader)
        // and DiagnosticCollector.addDiagnosticSafe is synchronized, so it is safe
        // to run every parseCommand concurrently with the same lookup.
        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for(var entry : functionSources.entrySet()) {
                final var key = entry.getKey();
                final var values = entry.getValue();
                final var arr = resultArrays.get(key);
                for(int i = 0; i < values.size(); i++) {
                    final int idx = i;
                    final String value = values.get(i);
                    executor.execute(() -> {
                        DiagnosticResult<CommandParams> res;
                        if(value.startsWith("$")) {
                            res = CommandParser.parseCommand(value.substring(1), key, true, lookup);
                        }else {
                            res = CommandParser.parseCommand(value, key, false, lookup);
                        }
                        if(res.isError()) {
                            var diag = res.getError();
                            assert diag != null;
                            diagnostics.addDiagnosticSafe(diag);
                            String rawCommand = value.startsWith("$") ? value.substring(1) : value;
                            arr[idx] = new CommandFallbackVanilla(rawCommand);
                            totalFailures.incrementAndGet();
                            parseFailures
                                .computeIfAbsent(diag.message(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                                .computeIfAbsent(key.namespace(), k -> new java.util.concurrent.atomic.AtomicInteger(0))
                                .incrementAndGet();
                        }else {
                            arr[idx] = Objects.requireNonNull(res.getResult());
                        }
                    });
                }
            }
        } // ExecutorService.close() blocks until every task finishes

        HashMap<DataLocation, List<CommandParams>> definedFunctions = new HashMap<>(resultArrays.size());
        for(var entry : resultArrays.entrySet()) {
            var arr = entry.getValue();
            ArrayList<CommandParams> parsed = new ArrayList<>(arr.length);
            for(var item : arr) parsed.add(item);
            definedFunctions.put(entry.getKey(), parsed);
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

    public static HashMap<DataLocation, List<Instruction>> compileFunctionsHighLevel(HashMap<DataLocation, List<CommandParams>> definedFunctions, DiagnosticCollector diagnostics, int @Nullable [] instructionCounts, boolean addReq) {
        var compiledFunctions = new java.util.concurrent.ConcurrentHashMap<DataLocation, List<Instruction>>();
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        // One virtual thread per function. parallelStream was unreliable here —
        // it always dispatches to commonPool regardless of enclosing pool, and
        // commonPool in the target runtime did not scale past 1 active worker.
        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for(var func : definedFunctions.entrySet()) {
                final var key = func.getKey();
                final var value = func.getValue();
                executor.execute(() -> {
                    try {
                        var compiled = CommandCompiler.compile(key, value);
                        if(instructionCounts != null) counter.addAndGet(compiled.size());
                        if(addReq) compiled = Instructions.addStaticRequirements(compiled);
                        compiledFunctions.put(key, compiled);
                    }catch(DiagnosticException e) {
                        LOGGER.warn("Failed to compile function {} / {}", key, e.getMessage());
                        diagnostics.addDiagnosticSafe(e.toDiagnostic());
                    }catch(Throwable e) {
                        LOGGER.error("Failed to compile function {}", key, e);
                        diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
                    }
                });
            }
        }
        if(instructionCounts != null) instructionCounts[0] = counter.get();
        return new HashMap<>(compiledFunctions);
    }

    public static HashMap<DataLocation, List<Instruction>> optimizeFunctionsHighLevel(HashMap<DataLocation, List<Instruction>> compiled, DiagnosticCollector diagnostics, int @Nullable [] instructionCounts, boolean addReq) {
        var compiledFunctions = new java.util.concurrent.ConcurrentHashMap<DataLocation, List<Instruction>>();
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        // Same VT executor pattern as compileFunctionsHighLevel above — see note.
        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for(var func : compiled.entrySet()) {
                final var key = func.getKey();
                final var value = func.getValue();
                executor.execute(() -> {
                    // LengauerTarjan is stateful — create a new instance per task
                    var tarjan = new LengauerTarjan();
                    try {
                        var optimized = Optimizer.optimize(value, tarjan);
                        if(instructionCounts != null) counter.addAndGet(optimized.size());
                        var finalOptimized = addReq ? Instructions.addStaticRequirements(optimized) : optimized;
                        compiledFunctions.put(key, finalOptimized);
                    }catch(DiagnosticException e) {
                        LOGGER.warn("Failed to optimize function {} / {}", key, e.getMessage());
                        diagnostics.addDiagnosticSafe(e.toDiagnostic());
                    }catch(Throwable e) {
                        LOGGER.error("Failed to optimize function {}", key, e);
                        diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
                    }
                });
            }
        }
        if(instructionCounts != null) instructionCounts[1] = counter.get();
        return new HashMap<>(compiledFunctions);
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

    // JVM method bytecode limit is 65535 bytes.
    // Most instructions average ~15-20 bytes; only extreme config-style functions hit ~80+.
    // Functions with 3455 instructions compile fine as single methods in practice.
    // Set threshold high enough to avoid unnecessary splitting while catching truly oversized functions.
    private static final int MAX_INSTRUCTIONS_PER_METHOD = 4000;
    private static final long NO_RETURN_SENTINEL = 0x2L << 32;

    @FunctionalInterface
    private interface MethodSink {
        void add(String name, List<BytecodeInstruction> instructions, MethodTypeDesc type);
    }

    /** Legacy API: 1 class per function. Preserved during the batching transition.
     * New code should use {@link #emitFunctionMethods}. */
    public static byte[] generateFunctionClass(CompilationParams params, DataLocation funcId, List<Instruction> instructions, Set<DataLocation> compiledFunctions, java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions) {
        var className = ClassDesc.ofInternalName(("mcfc/function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$'));
        return ClassGenerator.createClassGenerator(className, gen -> {
            MethodSink sink = gen::addFunction;
            emitInto(params, funcId, instructions, compiledFunctions, null, className, className.displayName(), "run", "runInstanced", sink, lowLevelFunctions);
        });
    }

    /** Batching API: emits all methods for one function (run + chunks + optional
     * macro-instanced body) as a {@link FunctionEmissionResult} to be assembled
     * later into a shared batch class. Cross-function invokestatic and chunk
     * dispatch resolve through {@code resolver} and {@code batchClass}. */
    public static FunctionEmissionResult emitFunctionMethods(
        CompilationParams params, DataLocation funcId, List<Instruction> instructions,
        Set<DataLocation> compiledFunctions, FunctionResolver resolver,
        BatchClassPlanner.PlacedFunction placement, ClassDesc batchClass,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions
    ) {
        var emitted = new ArrayList<MethodEmission>();
        MethodSink sink = (name, instr, type) -> emitted.add(new MethodEmission(name, instr, type));
        String base = placement.baseName();
        String instancedName = BatchClassPlanner.instancedMethodName(base);
        emitInto(params, funcId, instructions, compiledFunctions, resolver, batchClass, base, base, instancedName, sink, lowLevelFunctions);
        return new FunctionEmissionResult(funcId, placement.batchIndex(), emitted);
    }

    /** Core emit path shared by legacy and batching APIs.
     *
     * @param ownerClass     class that will own the emitted methods (for chunk invokestatic)
     * @param displayPrefix  prefix used when keying {@code lowLevelFunctions} entries
     * @param runName        name of the primary entry method
     * @param instancedName  name of the macro instanced method (unused if no macros)
     */
    private static void emitInto(
        CompilationParams params, DataLocation funcId, List<Instruction> instructions,
        Set<DataLocation> compiledFunctions,
        @Nullable FunctionResolver resolver,
        ClassDesc ownerClass, String displayPrefix, String runName, String instancedName,
        MethodSink sink, java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions
    ) {
        var macroInstructions = Instruction.findTyped(instructions, InstructionTypes.LOAD_MACRO);
        var macroInputs = new ArrayList<MacroInput>();
        for(var macroInstr : macroInstructions) {
            var template = macroInstr.<MacroTemplate<?>>get("macro");
            var macros = Objects.requireNonNull(template).extractMacros();
            for(var macro : macros) {
                var macroInput = new MacroInput(macro.source(), template.macroType());
                if(!macroInputs.contains(macroInput)) {
                    macroInputs.add(macroInput);
                }
            }
        }

        var nbtType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);
        var methodType = MethodTypeDesc.of(ConstantDescs.CD_long, params.commandSourceStackType(), nbtType);

        if(macroInstructions.isEmpty()) {
            if(instructions.size() <= MAX_INSTRUCTIONS_PER_METHOD) {
                emitSingleFunction(sink, params, funcId, compiledFunctions, resolver, instructions, lowLevelFunctions, displayPrefix, runName, nbtType, methodType);
            } else {
                emitSplitFunction(sink, params, funcId, compiledFunctions, resolver, instructions, lowLevelFunctions, ownerClass, displayPrefix, runName, methodType);
            }
            return;
        }

        // Macro entry trampoline — validates macros then invokes instancedName on ownerClass
        var entryWriter = new CodeWriter();
        var staticCommandSourceStack = entryWriter.createRegister();
        entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
        var staticFunctionCompound = entryWriter.createRegister();
        entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, nbtType));
        Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);

        var returnValue = entryWriter.createRegister();
        var instr = List.of(Instructions2.callFunctionsWithResult(LocationData.ofLocal(-3), List.of(funcId), LocationData.ofLocal(-1), LocationData.ofLocal(-2)));
        var entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, Collections.emptySet(), new HashMap<>(staticArguments), null, instr, resolver);
        entryContext.setTransform(-1, staticCommandSourceStack);
        entryContext.setTransform(-2, staticFunctionCompound);
        entryContext.setTransform(-3, returnValue);

        var errorLabel = entryWriter.createLabel();
        writeMacroInstanceCreation(params.stringGenerator(), ownerClass, instancedName, errorLabel, entryContext, macroInstructions, macroInputs, params.commandSourceStackType());
        entryWriter.label(errorLabel);
        entryContext.emitForEach();
        entryWriter.returnValue(returnValue);

        var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
        lowLevelFunctions.put(displayPrefix + "#" + runName, entryBytecode);
        sink.add(runName, entryBytecode, methodType);

        // Instanced body
        var writer = new CodeWriter();
        var staticCommandSourceStack2 = writer.createRegister();
        writer.setValue(staticCommandSourceStack2, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
        HashMap<String, BytecodeRegister> macroLocations = new HashMap<>();
        int i = 1;
        for(var macro : macroInputs) {
            var macroRegister = writer.createRegister();
            macroLocations.put(macro.source, macroRegister);
            writer.setValue(macroRegister, new BytecodeValue.Parameter(i, macro.type.getType().describeConstable().orElseThrow()));
            i++;
        }
        new EmitterContext(funcId, params.codeFieldStorage(), writer, compiledFunctions, new HashMap<>(Map.of("CommandSourceStack", staticCommandSourceStack2)), macroLocations, instructions, resolver).emitForEach();
        writer.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

        var macroedBytecode = BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
        lowLevelFunctions.put(displayPrefix + "#" + instancedName, macroedBytecode);
        List<ClassDesc> funcParams = new ArrayList<>();
        funcParams.add(params.commandSourceStackType());
        for(MacroInput macroInput : macroInputs) {
            funcParams.add(macroInput.type.getType().describeConstable().orElseThrow());
        }
        sink.add(instancedName, macroedBytecode, MethodTypeDesc.of(ConstantDescs.CD_long, funcParams));
    }

    private static void emitSingleFunction(
        MethodSink sink, CompilationParams params, DataLocation funcId,
        Set<DataLocation> compiledFunctions,
        @Nullable FunctionResolver resolver,
        List<Instruction> instructions,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        String displayPrefix, String runName, ClassDesc nbtType, MethodTypeDesc methodType
    ) {
        var entryWriter = new CodeWriter();
        var staticCommandSourceStack = entryWriter.createRegister();
        entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
        var staticFunctionCompound = entryWriter.createRegister();
        entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, nbtType));
        Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);
        var entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, compiledFunctions, new HashMap<>(staticArguments), null, instructions, resolver);
        entryContext.emitForEach();
        entryWriter.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

        var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
        lowLevelFunctions.put(displayPrefix + "#" + runName, entryBytecode);
        sink.add(runName, entryBytecode, methodType);
    }

    private static void emitSplitFunction(
        MethodSink sink, CompilationParams params, DataLocation funcId,
        Set<DataLocation> compiledFunctions,
        @Nullable FunctionResolver resolver,
        List<Instruction> instructions,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        ClassDesc ownerClass, String displayPrefix, String runName, MethodTypeDesc methodType
    ) {
        var nbtType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);

        // Extract the initialization prefix: leading instructions that write to locals
        // from static arguments only (no local reads, no label references).
        // In practice this is just GET_COMMAND_STACK at position 0. These must be replicated
        // at the start of every non-first chunk so each chunk independently accesses the CSS.
        int prefixEnd = 0;
        outer:
        for(int idx = 0; idx < instructions.size(); idx++) {
            var instr = instructions.get(idx);
            if(instr.data() != null) {
                for(int j = 0; j < instr.data().length; j++) {
                    var datum = instr.data()[j];
                    if(datum instanceof LocationData<?> loc && loc.local() != null
                            && !(j == 0 && instr.type().returnsValue())) {
                        prefixEnd = idx;
                        break outer;
                    }
                    if(datum instanceof LabelData) {
                        prefixEnd = idx;
                        break outer;
                    }
                }
            }
            if(idx == instructions.size() - 1) prefixEnd = instructions.size();
        }
        var initPrefix = instructions.subList(0, prefixEnd);

        // Find safe split points in the instruction list
        var splitPoints = findSafeInstructionSplitPoints(instructions);

        // Target chunk size: keep each chunk under the per-method instruction limit
        int targetChunkSize = MAX_INSTRUCTIONS_PER_METHOD;

        // Split into chunks at safe points
        var chunks = splitInstructionsIntoChunks(instructions, splitPoints, targetChunkSize);

        // Generate bytecode for each chunk in parallel — EmitterContext and BytecodeOptimizer
        // are fully stateless/thread-local, so this is safe. ClassBuilder (gen.addFunction) is
        // NOT thread-safe, so results are collected first and added to gen sequentially.
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<BytecodeInstruction>[] chunkBytecodes = new List[chunks.size()];
        java.util.stream.IntStream.range(0, chunks.size()).parallel().forEach(i -> {
            var chunkInstructions = chunks.get(i);
            List<Instruction> effectiveInstructions;
            if(i == 0 || initPrefix.isEmpty()) {
                effectiveInstructions = chunkInstructions;
            } else {
                var combined = new ArrayList<Instruction>(initPrefix.size() + chunkInstructions.size());
                combined.addAll(initPrefix);
                combined.addAll(chunkInstructions);
                effectiveInstructions = combined;
            }

            var writer = new CodeWriter();
            var css = writer.createRegister();
            writer.setValue(css, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
            var nbt = writer.createRegister();
            writer.setValue(nbt, new BytecodeValue.Parameter(1, nbtType));
            Map<String, BytecodeValue> args = Map.of("CommandSourceStack", css, "FunctionCompound", nbt);

            var context = new EmitterContext(funcId, params.codeFieldStorage(), writer, compiledFunctions, new HashMap<>(args), null, effectiveInstructions, resolver);
            context.emitForEach();
            writer.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

            chunkBytecodes[i] = BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
        });

        // Sequential pass: register in lowLevelFunctions and add to sink (ClassBuilder not thread-safe)
        for(int i = 0; i < chunks.size(); i++) {
            String chunkName = runName + "$chunk" + i;
            lowLevelFunctions.put(displayPrefix + "#" + chunkName, chunkBytecodes[i]);
            sink.add(chunkName, chunkBytecodes[i], methodType);
        }

        // Generate dispatch method that calls each chunk on ownerClass
        var dispatchWriter = new CodeWriter();
        var param0 = new BytecodeValue.Parameter(0, params.commandSourceStackType());
        var param1 = new BytecodeValue.Parameter(1, nbtType);

        for(int i = 0; i < chunks.size(); i++) {
            var chunkTarget = new InstructionTarget.FunctionTarget(
                ownerClass, runName + "$chunk" + i, methodType, true, false, false, true
            );
            var result = dispatchWriter.writeTarget(chunkTarget, List.of(param0, param1));

            if(i < chunks.size() - 1) {
                // If this chunk returned early (result != sentinel), propagate the return
                var continueLabel = dispatchWriter.createLabel();
                dispatchWriter.conditionalJump(JumpCondition.EQ, result, new BytecodeValue.LongValue(NO_RETURN_SENTINEL), continueLabel);
                dispatchWriter.returnValue(result);
                dispatchWriter.label(continueLabel);
            } else {
                // Last chunk — return its result directly
                dispatchWriter.returnValue(result);
            }
        }

        var dispatchBytecode = BytecodeOptimizer.optimizeInstructions(dispatchWriter.getInstructions());
        lowLevelFunctions.put(displayPrefix + "#" + runName, dispatchBytecode);
        sink.add(runName, dispatchBytecode, methodType);
    }

    /**
     * Find indices in the instruction list where it's safe to split.
     * A split point between instruction[i-1] and instruction[i] is safe if:
     * - No local written before the split is read after the split
     * - No label defined before the split is referenced after the split (and vice versa)
     *
     * Uses per-definition liveness for locals: if local slot L is written at positions
     * w0 < w1 < w2 ..., then the live range of definition w_k is [w_k, max_read_before_w_{k+1}].
     * This avoids falsely blocking splits when the same slot is reused for independent values
     * (e.g., a result register written and read by every command independently).
     *
     * Locals written in the "initialization prefix" are excluded from the live-range analysis.
     * The prefix consists of the leading instructions that write only to locals from static
     * arguments (no local reads, no label references) — in practice just the GET_COMMAND_STACK
     * instruction at position 0. These locals are re-initialized at the start of every chunk via
     * the chunk's own parameters, so they must not block split points across command boundaries.
     */
    private static List<Integer> findSafeInstructionSplitPoints(List<Instruction> instructions) {
        int n = instructions.size();

        // Identify the initialization prefix: leading instructions that write to locals
        // from static arguments only (no local reads, no label references).
        // In practice this is just the GET_COMMAND_STACK instruction at position 0.
        // These locals are re-initialized at the start of every chunk, so they must not
        // contribute live-range spans that block split points across command boundaries.
        int prefixEnd = 0;
        outer:
        for(int idx = 0; idx < n; idx++) {
            var instr = instructions.get(idx);
            if(instr.data() != null) {
                for(int j = 0; j < instr.data().length; j++) {
                    var datum = instr.data()[j];
                    // local read (not the result slot) → end of prefix
                    if(datum instanceof LocationData<?> loc && loc.local() != null
                            && !(j == 0 && instr.type().returnsValue())) {
                        prefixEnd = idx;
                        break outer;
                    }
                    // label reference → end of prefix
                    if(datum instanceof LabelData) {
                        prefixEnd = idx;
                        break outer;
                    }
                }
            }
            // If we never broke out, extend to next instruction
            if(idx == n - 1) prefixEnd = n;
        }
        var prefixLocals = new HashSet<Integer>();
        for(int idx = 0; idx < prefixEnd; idx++) {
            var instr = instructions.get(idx);
            if(instr.data() != null && instr.type().returnsValue()) {
                var datum = instr.data()[0];
                if(datum instanceof LocationData<?> loc && loc.local() != null) {
                    prefixLocals.add(loc.local());
                }
            }
        }

        // For each local slot: collect all write positions (sorted)
        var localWrites = new HashMap<Integer, List<Integer>>();
        // For each local slot at each position: is this position a read?
        var localReads = new HashMap<Integer, List<Integer>>();

        // For each label: track define position and reference range
        var labelDefine = new HashMap<Integer, Integer>();
        var labelMinRef = new HashMap<Integer, Integer>();
        var labelMaxRef = new HashMap<Integer, Integer>();

        for(int idx = 0; idx < n; idx++) {
            var instr = instructions.get(idx);
            if(instr.data() != null) {
                for(int j = 0; j < instr.data().length; j++) {
                    var datum = instr.data()[j];
                    if(datum instanceof LocationData<?> loc && loc.local() != null) {
                        if(j == 0 && instr.type().returnsValue()) {
                            localWrites.computeIfAbsent(loc.local(), k -> new ArrayList<>()).add(idx);
                        } else {
                            localReads.computeIfAbsent(loc.local(), k -> new ArrayList<>()).add(idx);
                        }
                    } else if(datum instanceof LabelData label) {
                        if(instr.type() == InstructionTypes.LABEL) {
                            labelDefine.put(label.id(), idx);
                        } else {
                            labelMinRef.merge(label.id(), idx, Math::min);
                            labelMaxRef.merge(label.id(), idx, Math::max);
                        }
                    }
                }
            }
        }

        // Delta array for sweep-line: delta[i]++ means a live range starts at i,
        // delta[i]-- means one ends before i.
        int[] delta = new int[n + 2];

        // Per-definition live ranges for locals.
        // For definition at write position w_k in slot L:
        //   - live range end = max read position of L that is < next write in L (or < n if last write)
        // If no reads exist in that window, the definition is dead immediately (range [w_k, w_k]).
        for(var entry : localWrites.entrySet()) {
            int slot = entry.getKey();
            // Prefix locals are re-initialized in each chunk from chunk parameters; skip them.
            if(prefixLocals.contains(slot)) continue;
            var writes = entry.getValue(); // already in insertion order (ascending idx)
            var reads = localReads.getOrDefault(slot, List.of());

            for(int wi = 0; wi < writes.size(); wi++) {
                int writePos = writes.get(wi);
                int nextWritePos = (wi + 1 < writes.size()) ? writes.get(wi + 1) : n;

                // Find max read position strictly between writePos and nextWritePos
                int maxRead = writePos; // no reads → range is trivial [writePos, writePos]
                for(int rp : reads) {
                    if(rp > writePos && rp < nextWritePos) {
                        maxRead = Math.max(maxRead, rp);
                    }
                }

                if(writePos < maxRead) {
                    delta[writePos + 1]++;
                    delta[maxRead + 1]--;
                }
                // else: definition is dead before next write — no range to block
            }
        }

        // Label ranges: [min(def, minRef), max(def, maxRef)]
        var allLabelIds = new HashSet<Integer>();
        allLabelIds.addAll(labelDefine.keySet());
        allLabelIds.addAll(labelMinRef.keySet());
        allLabelIds.addAll(labelMaxRef.keySet());

        for(var labelId : allLabelIds) {
            Integer defPos = labelDefine.get(labelId);
            Integer minRef = labelMinRef.get(labelId);
            Integer maxRef = labelMaxRef.get(labelId);

            int rangeStart = Integer.MAX_VALUE;
            int rangeEnd = Integer.MIN_VALUE;
            if(defPos != null) { rangeStart = defPos; rangeEnd = defPos; }
            if(minRef != null) { rangeStart = Math.min(rangeStart, minRef); rangeEnd = Math.max(rangeEnd, minRef); }
            if(maxRef != null) { rangeStart = Math.min(rangeStart, maxRef); rangeEnd = Math.max(rangeEnd, maxRef); }

            if(rangeStart < rangeEnd) {
                delta[rangeStart + 1]++;
                delta[rangeEnd + 1]--;
            }
        }

        // Sweep: split at position i is safe when no live ranges cross it
        var safePoints = new ArrayList<Integer>();
        int active = 0;
        for(int i = 1; i < n; i++) {
            active += delta[i];
            if(active == 0) {
                safePoints.add(i);
            }
        }

        return safePoints;
    }

    /**
     * Split instructions into chunks at safe split points, trying to keep each chunk
     * under the target size.
     */
    private static List<List<Instruction>> splitInstructionsIntoChunks(
        List<Instruction> instructions, List<Integer> safeSplitPoints, int targetChunkSize
    ) {
        var chunks = new ArrayList<List<Instruction>>();
        int chunkStart = 0;

        for(int splitPoint : safeSplitPoints) {
            if(splitPoint - chunkStart >= targetChunkSize) {
                chunks.add(instructions.subList(chunkStart, splitPoint));
                chunkStart = splitPoint;
            }
        }

        // Add remaining instructions as the last chunk
        if(chunkStart < instructions.size()) {
            chunks.add(instructions.subList(chunkStart, instructions.size()));
        }

        // If we couldn't split at all, return the whole thing as one chunk
        if(chunks.isEmpty()) {
            chunks.add(instructions);
        }

        return chunks;
    }

}
