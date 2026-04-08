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
        HashMap<DataLocation, List<CommandParams>> definedFunctions = new HashMap<>();
        HashMap<String, HashMap<String, Integer>> parseFailures = new HashMap<>();
        int totalFailures = 0;
        for(var entry : functionSources.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            ArrayList<CommandParams> parsed = new ArrayList<>();
            for(String value : values) {
                DiagnosticResult<CommandParams> res;
                if(value.startsWith("$")) {
                    res = CommandParser.parseCommand(value.substring(1), key, true, lookup);
                }else {
                    res = CommandParser.parseCommand(value, key, false, lookup);
                }
                if(res.isError()) {
                    var diag = res.getError();
                    assert diag != null;
                    diagnostics.addDiagnostic(diag);
                    String rawCommand = value.startsWith("$") ? value.substring(1) : value;
                    parsed.add(new CommandFallbackVanilla(rawCommand));
                    totalFailures++;
                    parseFailures
                        .computeIfAbsent(diag.message(), k -> new HashMap<>())
                        .merge(key.namespace(), 1, Integer::sum);
                }else {
                    parsed.add(Objects.requireNonNull(res.getResult()));
                }
            }
            definedFunctions.put(key, parsed);
        }
        if(totalFailures > 0) {
            var summary = new StringBuilder();
            summary.append("Partially compiled ").append(totalFailures).append(" command(s) with vanilla fallback. Reasons:");
            var sortedEntries = new ArrayList<>(parseFailures.entrySet());
            sortedEntries.sort((a, b) -> {
                int countA = a.getValue().values().stream().mapToInt(Integer::intValue).sum();
                int countB = b.getValue().values().stream().mapToInt(Integer::intValue).sum();
                return Integer.compare(countB, countA);
            });
            for(var reasonEntry : sortedEntries) {
                int reasonTotal = reasonEntry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                summary.append("\n  ").append(reasonEntry.getKey()).append(": ").append(reasonTotal).append(" command(s)");
                var namespaces = new ArrayList<>(reasonEntry.getValue().entrySet());
                namespaces.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                summary.append(" (");
                for(int i = 0; i < namespaces.size(); i++) {
                    if(i > 0) summary.append(", ");
                    summary.append(namespaces.get(i).getKey()).append(": ").append(namespaces.get(i).getValue());
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
        definedFunctions.entrySet().parallelStream().forEach(func -> {
            var key = func.getKey();
            var value = func.getValue();
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
        if(instructionCounts != null) instructionCounts[0] = counter.get();
        return new HashMap<>(compiledFunctions);
    }

    public static HashMap<DataLocation, List<Instruction>> optimizeFunctionsHighLevel(HashMap<DataLocation, List<Instruction>> compiled, DiagnosticCollector diagnostics, int @Nullable [] instructionCounts, boolean addReq) {
        var compiledFunctions = new java.util.concurrent.ConcurrentHashMap<DataLocation, List<Instruction>>();
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        compiled.entrySet().parallelStream().forEach(func -> {
            // LengauerTarjan is stateful — create a new instance per thread
            var tarjan = new LengauerTarjan();
            var key = func.getKey();
            var value = func.getValue();
            try {
                var optimized = Optimizer.optimize(value, tarjan);
                if(instructionCounts != null) counter.addAndGet(optimized.size());
                if(addReq) optimized = Instructions.addStaticRequirements(optimized);
                compiledFunctions.put(key, optimized);
            }catch(DiagnosticException e) {
                LOGGER.warn("Failed to optimize function {} / {}", key, e.getMessage());
                diagnostics.addDiagnosticSafe(e.toDiagnostic());
            }catch(Throwable e) {
                LOGGER.error("Failed to optimize function {}", key, e);
                diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
            }
        });
        if(instructionCounts != null) instructionCounts[1] = counter.get();
        return new HashMap<>(compiledFunctions);
    }

    public interface NbtTagBytecodeStringGenerator {
        BytecodeValue nbtTagToBytecodeString(EmitterContext context, String key, @Nullable BytecodeLabel errorLabel);
    }

    private record MacroInput(String source, MacroType<?> type) {}

    private static void writeMacroInstanceCreation(NbtTagBytecodeStringGenerator nbtStringGenerator, ClassDesc className, BytecodeLabel errorLabel, EmitterContext context, List<Instruction> macroInstructions, List<MacroInput> macroInputs, ClassDesc commandSourceStackType) {
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

        var target = new InstructionTarget.FunctionTarget(className, "runInstanced",  MethodTypeDesc.of(ConstantDescs.CD_long, targetArgs), true, false, false, true);
        context.codeWriter().returnValue(context.codeWriter().writeTarget(target, arguments));
    }

    // JVM method bytecode limit is 65535 bytes.
    // Most instructions average ~15-20 bytes; only extreme config-style functions hit ~80+.
    // Functions with 3455 instructions compile fine as single methods in practice.
    // Set threshold high enough to avoid unnecessary splitting while catching truly oversized functions.
    private static final int MAX_INSTRUCTIONS_PER_METHOD = 4000;
    private static final long NO_RETURN_SENTINEL = 0x2L << 32;

    public static byte[] generateFunctionClass(CompilationParams params, DataLocation funcId, List<Instruction> instructions, Set<DataLocation> compiledFunctions, java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions) {
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

        var className = ClassDesc.ofInternalName(("mcfc/function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$'));
        var nbtType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);
        var methodType = MethodTypeDesc.of(ConstantDescs.CD_long, params.commandSourceStackType(), nbtType);

        return ClassGenerator.createClassGenerator(className, gen -> {
            if(macroInstructions.isEmpty()) {
                // Check instruction count upfront to avoid expensive full bytecode generation for huge functions
                if(instructions.size() <= MAX_INSTRUCTIONS_PER_METHOD) {
                    generateSingleFunction(gen, params, funcId, compiledFunctions, instructions, lowLevelFunctions, className, nbtType, methodType);
                } else {
                    LOGGER.info("Function {} has {} instructions (limit {}), splitting into chunks", funcId, instructions.size(), MAX_INSTRUCTIONS_PER_METHOD);
                    generateSplitFunction(gen, params, funcId, compiledFunctions, instructions, lowLevelFunctions, className, methodType);
                }
            } else {
                // Macro entry point — generates a trampoline that validates macros then calls runInstanced
                var entryWriter = new CodeWriter();
                var staticCommandSourceStack = entryWriter.createRegister();
                entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
                var staticFunctionCompound = entryWriter.createRegister();
                entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, nbtType));
                Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);

                var returnValue = entryWriter.createRegister();
                var instr = List.of(Instructions2.callFunctionsWithResult(LocationData.ofLocal(-3), List.of(funcId), LocationData.ofLocal(-1), LocationData.ofLocal(-2)));
                var entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, Collections.emptySet(), new HashMap<>(staticArguments), null, instr);
                entryContext.setTransform(-1, staticCommandSourceStack);
                entryContext.setTransform(-2, staticFunctionCompound);
                entryContext.setTransform(-3, returnValue);

                var errorLabel = entryWriter.createLabel();
                writeMacroInstanceCreation(params.stringGenerator(), className, errorLabel, entryContext, macroInstructions, macroInputs, params.commandSourceStackType());
                entryWriter.label(errorLabel);
                entryContext.emitForEach();
                entryWriter.returnValue(returnValue);

                var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
                lowLevelFunctions.put(className.displayName() + "#run", entryBytecode);
                gen.addFunction("run", entryBytecode, methodType);

                // runInstanced — may also need splitting
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
                new EmitterContext(funcId, params.codeFieldStorage(), writer, compiledFunctions, new HashMap<>(Map.of("CommandSourceStack", staticCommandSourceStack2)), macroLocations, instructions).emitForEach();
                writer.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

                var macroedBytecode = BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
                lowLevelFunctions.put(className.displayName() + "#runInstanced", macroedBytecode);
                List<ClassDesc> funcParams = new ArrayList<>();
                funcParams.add(params.commandSourceStackType());
                for(MacroInput macroInput : macroInputs) {
                    funcParams.add(macroInput.type.getType().describeConstable().orElseThrow());
                }
                gen.addFunction("runInstanced", macroedBytecode, MethodTypeDesc.of(ConstantDescs.CD_long, funcParams));
            }
        });
    }

    private static void generateSingleFunction(
        ClassGenerator gen, CompilationParams params, DataLocation funcId,
        Set<DataLocation> compiledFunctions, List<Instruction> instructions,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        ClassDesc className, ClassDesc nbtType, MethodTypeDesc methodType
    ) {
        var entryWriter = new CodeWriter();
        var staticCommandSourceStack = entryWriter.createRegister();
        entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
        var staticFunctionCompound = entryWriter.createRegister();
        entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, nbtType));
        Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);
        var entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, compiledFunctions, new HashMap<>(staticArguments), null, instructions);
        entryContext.emitForEach();
        entryWriter.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

        var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
        lowLevelFunctions.put(className.displayName() + "#run", entryBytecode);
        gen.addFunction("run", entryBytecode, methodType);
    }

    private static void generateSplitFunction(
        ClassGenerator gen, CompilationParams params, DataLocation funcId,
        Set<DataLocation> compiledFunctions, List<Instruction> instructions,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        ClassDesc className, MethodTypeDesc methodType
    ) {
        var nbtType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);

        // Extract the initialization prefix: instructions before the first LABEL.
        // These initialize function-scope locals (e.g., commandStack from GET_COMMAND_STACK)
        // from static arguments and must be replicated at the start of every non-first chunk
        // so that each chunk can independently access the correct CommandSourceStack.
        int prefixEnd = 0;
        for(int idx = 0; idx < instructions.size(); idx++) {
            if(instructions.get(idx).type() == InstructionTypes.LABEL) {
                prefixEnd = idx;
                break;
            }
        }
        var initPrefix = instructions.subList(0, prefixEnd);

        // Find safe split points in the instruction list
        var splitPoints = findSafeInstructionSplitPoints(instructions);

        // Target chunk size: keep each chunk under the per-method instruction limit
        int totalInstructions = instructions.size();
        int targetChunkSize = MAX_INSTRUCTIONS_PER_METHOD;

        // Split into chunks at safe points
        var chunks = splitInstructionsIntoChunks(instructions, splitPoints, targetChunkSize);
        LOGGER.info("Split function {} into {} chunks ({} instructions)", funcId, chunks.size(), totalInstructions);

        // Generate each chunk method
        for(int i = 0; i < chunks.size(); i++) {
            var chunkInstructions = chunks.get(i);
            // Non-first chunks need the init prefix prepended so their function-scope locals
            // (commandStack, etc.) are initialized from the chunk's own parameters.
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

            var context = new EmitterContext(funcId, params.codeFieldStorage(), writer, compiledFunctions, new HashMap<>(args), null, effectiveInstructions);
            context.emitForEach();
            writer.returnValue(new BytecodeValue.LongValue(NO_RETURN_SENTINEL));

            var bytecode = BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
            lowLevelFunctions.put(className.displayName() + "#run$chunk" + i, bytecode);
            gen.addFunction("run$chunk" + i, bytecode, methodType);
        }

        // Generate dispatch method that calls each chunk
        var dispatchWriter = new CodeWriter();
        var param0 = new BytecodeValue.Parameter(0, params.commandSourceStackType());
        var param1 = new BytecodeValue.Parameter(1, nbtType);

        for(int i = 0; i < chunks.size(); i++) {
            var chunkTarget = new InstructionTarget.FunctionTarget(
                className, "run$chunk" + i, methodType, true, false, false, true
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
        lowLevelFunctions.put(className.displayName() + "#run", dispatchBytecode);
        gen.addFunction("run", dispatchBytecode, methodType);
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
     * Locals written in the "initialization prefix" (all instructions before the first LABEL) are
     * excluded from the live-range analysis. These are function-scope context locals (e.g.,
     * commandStack from GET_COMMAND_STACK) that are re-initialized at the start of every chunk via
     * the chunk's own parameters, so they must not block split points across command boundaries.
     */
    private static List<Integer> findSafeInstructionSplitPoints(List<Instruction> instructions) {
        int n = instructions.size();

        // Identify the initialization prefix: instructions before the first LABEL.
        // Locals written here (e.g. commandStack) will be re-initialized in every chunk,
        // so they must not contribute live-range spans that block all split points.
        int prefixEnd = 0;
        for(int idx = 0; idx < n; idx++) {
            if(instructions.get(idx).type() == InstructionTypes.LABEL) {
                prefixEnd = idx;
                break;
            }
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
            if(defPos != null) { rangeStart = Math.min(rangeStart, defPos); rangeEnd = Math.max(rangeEnd, defPos); }
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
