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

    // JVM method bytecode limit is 65535 bytes. Use a conservative threshold for cost estimation.
    private static final int MAX_BYTECODE_COST = 50000;
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
                // Try generating as a single method first
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
                int estimatedSize = estimateBytecodeSize(entryBytecode);

                if(estimatedSize <= MAX_BYTECODE_COST) {
                    lowLevelFunctions.put(className.displayName() + "#run", entryBytecode);
                    gen.addFunction("run", entryBytecode, methodType);
                } else {
                    LOGGER.info("Function {} has estimated bytecode size {} (limit {}), splitting into chunks", funcId, estimatedSize, MAX_BYTECODE_COST);
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

    private static void generateSplitFunction(
        ClassGenerator gen, CompilationParams params, DataLocation funcId,
        Set<DataLocation> compiledFunctions, List<Instruction> instructions,
        java.util.Map<String, List<BytecodeInstruction>> lowLevelFunctions,
        ClassDesc className, MethodTypeDesc methodType
    ) {
        var nbtType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);

        // Find safe split points in the instruction list
        var splitPoints = findSafeInstructionSplitPoints(instructions);

        // Calculate target number of chunks
        // Estimate total cost: generate bytecode for the full function and measure
        int totalInstructions = instructions.size();
        int targetChunkSize = Math.max(1, totalInstructions / 8); // Start with 8 chunks

        // Split into chunks at safe points
        var chunks = splitInstructionsIntoChunks(instructions, splitPoints, targetChunkSize);
        LOGGER.info("Split function {} into {} chunks ({} instructions)", funcId, chunks.size(), totalInstructions);

        // Generate each chunk method
        for(int i = 0; i < chunks.size(); i++) {
            var chunkInstructions = chunks.get(i);
            var writer = new CodeWriter();
            var css = writer.createRegister();
            writer.setValue(css, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
            var nbt = writer.createRegister();
            writer.setValue(nbt, new BytecodeValue.Parameter(1, nbtType));
            Map<String, BytecodeValue> args = Map.of("CommandSourceStack", css, "FunctionCompound", nbt);

            var context = new EmitterContext(funcId, params.codeFieldStorage(), writer, compiledFunctions, new HashMap<>(args), null, chunkInstructions);
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

    private static int estimateBytecodeSize(List<BytecodeInstruction> instructions) {
        int cost = 0;
        for(var instr : instructions) {
            cost += instr.cost();
        }
        return cost;
    }

    /**
     * Find indices in the instruction list where it's safe to split.
     * A split point between instruction[i-1] and instruction[i] is safe if:
     * - No local written before the split is read after the split
     * - No label defined before the split is referenced after the split (and vice versa)
     */
    private static List<Integer> findSafeInstructionSplitPoints(List<Instruction> instructions) {
        int n = instructions.size();

        // Pre-compute: for each instruction, which locals it reads and writes, which labels it defines/references
        var instrWritesLocal = new ArrayList<Set<Integer>>();
        var instrReadsLocal = new ArrayList<Set<Integer>>();
        var instrDefinesLabel = new ArrayList<Set<Integer>>();
        var instrReferencesLabel = new ArrayList<Set<Integer>>();

        for(var instr : instructions) {
            var writes = new HashSet<Integer>();
            var reads = new HashSet<Integer>();
            var defines = new HashSet<Integer>();
            var references = new HashSet<Integer>();

            if(instr.data() != null) {
                for(int j = 0; j < instr.data().length; j++) {
                    var datum = instr.data()[j];
                    if(datum instanceof LocationData<?> loc && loc.local() != null) {
                        // First arg named "result" is a write, others are reads
                        if(j == 0 && instr.type().returnsValue()) {
                            writes.add(loc.local());
                        } else {
                            reads.add(loc.local());
                        }
                    } else if(datum instanceof LabelData label) {
                        if(instr.type() == InstructionTypes.LABEL) {
                            defines.add(label.id());
                        } else {
                            references.add(label.id());
                        }
                    }
                }
            }

            instrWritesLocal.add(writes);
            instrReadsLocal.add(reads);
            instrDefinesLabel.add(defines);
            instrReferencesLabel.add(references);
        }

        // Compute suffix sets: for each position i, what locals are read in [i..n) and labels are referenced/defined in [i..n)
        var suffixReads = new HashSet<Integer>();
        var suffixRefs = new HashSet<Integer>();
        var suffixDefs = new HashSet<Integer>();
        var suffixReadAt = new ArrayList<Set<Integer>>(Collections.nCopies(n + 1, Set.of()));
        var suffixRefAt = new ArrayList<Set<Integer>>(Collections.nCopies(n + 1, Set.of()));
        var suffixDefAt = new ArrayList<Set<Integer>>(Collections.nCopies(n + 1, Set.of()));

        for(int i = n - 1; i >= 0; i--) {
            suffixReads.addAll(instrReadsLocal.get(i));
            suffixRefs.addAll(instrReferencesLabel.get(i));
            suffixDefs.addAll(instrDefinesLabel.get(i));
            suffixReadAt.set(i, new HashSet<>(suffixReads));
            suffixRefAt.set(i, new HashSet<>(suffixRefs));
            suffixDefAt.set(i, new HashSet<>(suffixDefs));
        }

        // Compute prefix sets incrementally
        var prefixWrites = new HashSet<Integer>();
        var prefixDefs = new HashSet<Integer>();
        var prefixRefs = new HashSet<Integer>();

        var safePoints = new ArrayList<Integer>();
        for(int i = 1; i < n; i++) {
            prefixWrites.addAll(instrWritesLocal.get(i - 1));
            prefixDefs.addAll(instrDefinesLabel.get(i - 1));
            prefixRefs.addAll(instrReferencesLabel.get(i - 1));

            // Check: no local written in [0..i) is read in [i..n)
            boolean localsCross = false;
            for(var written : prefixWrites) {
                if(suffixReadAt.get(i).contains(written)) {
                    localsCross = true;
                    break;
                }
            }
            if(localsCross) continue;

            // Check: no label defined in [0..i) is referenced in [i..n)
            boolean labelsCrossForward = false;
            for(var defined : prefixDefs) {
                if(suffixRefAt.get(i).contains(defined)) {
                    labelsCrossForward = true;
                    break;
                }
            }
            if(labelsCrossForward) continue;

            // Check: no label referenced in [0..i) is defined in [i..n)
            boolean labelsCrossBackward = false;
            for(var referenced : prefixRefs) {
                if(suffixDefAt.get(i).contains(referenced)) {
                    labelsCrossBackward = true;
                    break;
                }
            }
            if(labelsCrossBackward) continue;

            safePoints.add(i);
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
