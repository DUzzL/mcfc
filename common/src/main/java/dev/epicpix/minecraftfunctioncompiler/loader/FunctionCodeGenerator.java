package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParser;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroType;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
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
        nextFunction: for(var entry : functionSources.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            String currentCommand = null;
            try {
                ArrayList<CommandParams> parsed = new ArrayList<>();
                for(String value : values) {
                    currentCommand = value;
                    DiagnosticResult<CommandParams> res;
                    if(currentCommand.startsWith("$")) {
                        res = CommandParser.parseCommand(currentCommand.substring(1), key, true, lookup);
                    }else {
                        res = CommandParser.parseCommand(currentCommand, key, false, lookup);
                    }
                    if(res.isError()) {
                        var diag = res.getError();
                        assert diag != null;
                        LOGGER.warn("Unable to parse function {} / {}", key, diag.message());
                        diagnostics.addDiagnostic(diag);
                        continue nextFunction;
                    }
                    parsed.add(Objects.requireNonNull(res.getResult()));
                }
                definedFunctions.put(key, parsed);
            } catch (DiagnosticException e) {
                LOGGER.warn("Unable to parse function {} / {} / {}", key, e.getMessage(), currentCommand);
                diagnostics.addDiagnostic(e.toDiagnostic());
            } catch (Throwable e) {
                LOGGER.error("Unable to parse function {}", key, e);
                assert currentCommand != null;
                diagnostics.addDiagnostic(Diagnostic.parseError(UNSPECIFIED_COMMAND_ERROR, key, currentCommand, e.getMessage(), e));
            }
        }
        return definedFunctions;
    }

    public static HashMap<DataLocation, List<Instruction>> compileFunctionsHighLevel(HashMap<DataLocation, List<CommandParams>> definedFunctions, DiagnosticCollector diagnostics, int @Nullable [] instructionCounts, boolean addReq) {
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        for(var func : definedFunctions.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var compiled = CommandCompiler.compile(key, value);
                if(instructionCounts != null) instructionCounts[0] += compiled.size();
                if(addReq) compiled = Instructions.addStaticRequirements(compiled);
                compiledFunctions.put(key, compiled);
            }catch(DiagnosticException e) {
                LOGGER.warn("Failed to compile function {} / {}", key, e.getMessage());
                diagnostics.addDiagnostic(e.toDiagnostic());
            }catch(Throwable e) {
                LOGGER.error("Failed to compile function {}", key, e);
                diagnostics.addDiagnostic(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
            }
        }
        return compiledFunctions;
    }

    public static HashMap<DataLocation, List<Instruction>> optimizeFunctionsHighLevel(HashMap<DataLocation, List<Instruction>> compiled, DiagnosticCollector diagnostics, int @Nullable [] instructionCounts, boolean addReq) {
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        var tarjan = new LengauerTarjan();
        for(var func : compiled.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var optimized = Optimizer.optimize(value, tarjan);
                if(instructionCounts != null) instructionCounts[1] += optimized.size();
                if(addReq) optimized = Instructions.addStaticRequirements(optimized);
                compiledFunctions.put(key, optimized);
            }catch(DiagnosticException e) {
                LOGGER.warn("Failed to optimize function {} / {}", key, e.getMessage());
                diagnostics.addDiagnostic(e.toDiagnostic());
            }catch(Throwable e) {
                LOGGER.error("Failed to optimize function {}", key, e);
                diagnostics.addDiagnostic(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
            }
        }
        return compiledFunctions;
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

    public static byte[] generateFunctionClass(CompilationParams params, DataLocation funcId, List<Instruction> instructions, Set<DataLocation> compiledFunctions, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions) {
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

        return ClassGenerator.createClassGenerator(className, gen -> {
            var entryWriter = new CodeWriter();
            var staticCommandSourceStack = entryWriter.createRegister();
            entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, params.commandSourceStackType()));
            var staticFunctionCompound = entryWriter.createRegister();
            entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND)));
            Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);
            EmitterContext entryContext;
            if(macroInstructions.isEmpty()) {
                entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, compiledFunctions, new HashMap<>(staticArguments), null, instructions);
                entryContext.emitForEach();
                entryWriter.returnValue(new BytecodeValue.LongValue(0x2L << 32));
            }else {
                var returnValue = entryWriter.createRegister();
                var instr = List.of(Instructions2.callFunctionsWithResult(LocationData.ofLocal(-3), List.of(funcId), LocationData.ofLocal(-1), LocationData.ofLocal(-2)));
                entryContext = new EmitterContext(funcId, params.codeFieldStorage(), entryWriter, Collections.emptySet(), new HashMap<>(staticArguments), null, instr);
                entryContext.setTransform(-1, staticCommandSourceStack);
                entryContext.setTransform(-2, staticFunctionCompound);
                entryContext.setTransform(-3, returnValue);

                var errorLabel = entryWriter.createLabel();
                writeMacroInstanceCreation(params.stringGenerator(), className, errorLabel, entryContext, macroInstructions, macroInputs, params.commandSourceStackType());
                entryWriter.label(errorLabel);
                entryContext.emitForEach();
                entryWriter.returnValue(returnValue);
            }

            var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
            lowLevelFunctions.put(className.displayName() + "#run", entryBytecode);
            gen.addFunction("run", entryBytecode, MethodTypeDesc.of(ConstantDescs.CD_long, params.commandSourceStackType(), params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND)));


            if(!macroInstructions.isEmpty()) {
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

                writer.returnValue(new BytecodeValue.LongValue(0x2L << 32));


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

}
