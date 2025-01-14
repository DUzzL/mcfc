package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.*;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
                if(MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_20_2, null)) {
                    while(value.toString().endsWith("\\")) {
                        String next = values.get(++i).trim();
                        value.deleteCharAt(value.length() - 1);
                        value.append(next);
                    }
                }
                if(value.isEmpty() || value.charAt(0) == '#') continue;
                commands.add(value.toString());
            }
            readFunctions.put(key, commands);
        }
        return readFunctions;
    }

    public static HashMap<DataLocation, List<CommandParams>> parseFunctions(HashMap<DataLocation, List<String>> functionSources, IArgumentLookup lookup, ArrayList<CommandError> commandErrors) {
        HashMap<DataLocation, List<CommandParams>> definedFunctions = new HashMap<>();
        for(var entry : functionSources.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            String currentCommand = null;
            try {
                ArrayList<CommandParams> parsed = new ArrayList<>();
                for(String value : values) {
                    currentCommand = value;
                    if(MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_20_2, null) && currentCommand.startsWith("$")) {
                        parsed.add(CommandParser.parseCommand(currentCommand.substring(1), true, lookup));
                    }else {
                        parsed.add(CommandParser.parseCommand(currentCommand, false, lookup));
                    }
                }
                definedFunctions.put(key, parsed);
            } catch (CommandErrorException e) {
                LOGGER.warn("Unable to parse function {} / {}", key, e.getMessage());
                commandErrors.add(e.error);
            } catch (StringCommandReaderException e) {
                LOGGER.warn("Unable to parse function {} / {} / {}", key, e.getMessage(), currentCommand);
                commandErrors.add(new CommandError(currentCommand, e.getMessage()));
            } catch (Throwable e) {
                LOGGER.error("Unable to parse function {}", key, e);
                commandErrors.add(new CommandError(currentCommand, e.getMessage(), CommandError.Step.PARSE, e));
            }
        }
        return definedFunctions;
    }

    public static HashMap<DataLocation, List<Instruction>> compileFunctionsHighLevel(HashMap<DataLocation, List<CommandParams>> definedFunctions, ArrayList<CommandError> commandErrors, int[] instructionCounts) {
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        for(var func : definedFunctions.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var compiled = CommandCompiler.compile(value);
                if(instructionCounts != null) instructionCounts[0] += compiled.size();
                var optimized = Optimizer.optimize(compiled);
                if(instructionCounts != null) instructionCounts[1] += optimized.size();
                compiledFunctions.put(key, Instructions2.addStaticRequirements(optimized));
            }catch(UnsupportedOperationException e) {
                LOGGER.warn("Failed to compile function {} / {}", key, e.getMessage());
                commandErrors.add(new CommandError(e.getMessage()));
            }catch(Throwable e) {
                LOGGER.error("Failed to compile function {}", key, e);
                commandErrors.add(new CommandError(null, e.getMessage(), CommandError.Step.COMPILE, e));
            }
        }
        return compiledFunctions;
    }

    public interface NbtTagBytecodeStringGenerator {
        BytecodeValue nbtTagToBytecodeString(EmitterContext context, String key, BytecodeLabel errorLabel);
    }

    private static void writeMacroInstanceCreation(NbtTagBytecodeStringGenerator nbtStringGenerator, String className, BytecodeLabel errorLabel, EmitterContext context, List<Instruction> macroInstructions, List<String> macroInputs, Type commandSourceStackType) {
        context.codeWriter().conditionalJump(JumpCondition.EQ, context.getStatic("FunctionCompound"), new BytecodeValue.NullValue(), errorLabel);

        HashMap<String, HashSet<MacroValidator>> generatedValidators = new HashMap<>();

        for(var macroInstr : macroInstructions) {
            var macros = macroInstr.<MacroTemplate<String>>get("macroTemplate").extractMacros();
            for(var macro : macros) {
                var generated = generatedValidators.computeIfAbsent(macro.source(), k -> new HashSet<>());
                if(generated.containsAll(macro.validators())) {
                    continue;
                }
                var value = nbtStringGenerator.nbtTagToBytecodeString(context, macro.source(), errorLabel);
                for(var validator : macro.validators()) {
                    if(generated.add(validator)) {
                        validator.validateGenerated(value, context.codeWriter(), context.fieldStorage(), errorLabel);
                    }
                }
            }
        }

        List<BytecodeValue> arguments = new ArrayList<>();
        arguments.add(context.getStatic("CommandSourceStack"));
        for(String macro : macroInputs) {
            arguments.add(nbtStringGenerator.nbtTagToBytecodeString(context, macro, null));
        }

        var target = new InstructionTarget.FunctionTarget(className, "runInstanced",  "(" + commandSourceStackType.getDescriptor() + "Ljava/lang/String;".repeat(macroInputs.size()) + ")J", true, false, false, true);
        context.codeWriter().returnValue(context.codeWriter().writeTarget(target, arguments));
    }

    public static ClassGenerator generateFunctionClass(NbtTagBytecodeStringGenerator nbtStringGenerator, DataLocation funcId, CodeFieldStorage fieldStorage, List<Instruction> instructions, int commandCount, Set<DataLocation> compiledFunctions, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions, Type commandSourceStackType) {
        var macroInstructions = Instruction.findTyped(instructions, InstructionTypes.LOAD_MACRO);
        var macroInputs = new ArrayList<String>();
        for(var macroInstr : macroInstructions) {
            var macros = macroInstr.<MacroTemplate<String>>get("macroTemplate").extractMacros();
            for(var macro : macros) {
                if(!macroInputs.contains(macro.source())) {
                    macroInputs.add(macro.source());
                }
            }
        }

        var className = ("mcfc/function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$');

        var gen = ClassGenerator.createClassGenerator(className);


        var entryWriter = new CodeWriter();
        var staticCommandSourceStack = entryWriter.createRegister();
        entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, commandSourceStackType));
        var staticFunctionCompound = entryWriter.createRegister();
        entryWriter.setValue(staticFunctionCompound, new BytecodeValue.Parameter(1, fieldStorage.getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND)));
        Map<String, BytecodeValue> staticArguments = Map.of("CommandSourceStack", staticCommandSourceStack, "FunctionCompound", staticFunctionCompound);
        EmitterContext entryContext;
        if(macroInstructions.isEmpty()) {
            entryContext = new EmitterContext(fieldStorage, entryWriter, compiledFunctions, new HashMap<>(staticArguments), null, instructions);
        }else {
            var instr = List.of(Instructions.callFunctions(List.of(funcId), LocationData.ofLocal(-1), LocationData.ofLocal(-2)));
            entryContext = new EmitterContext(fieldStorage, entryWriter, Collections.emptySet(), new HashMap<>(staticArguments), null, instr);
            entryContext.setTransform(-1, staticCommandSourceStack);
            entryContext.setTransform(-2, staticFunctionCompound);

            var errorLabel = entryWriter.createLabel();
            writeMacroInstanceCreation(nbtStringGenerator, className, errorLabel, entryContext, macroInstructions, macroInputs, commandSourceStackType);
            entryWriter.label(errorLabel);
        }
        entryContext.emitForEach();
        entryWriter.returnValue(new BytecodeValue.LongValue((commandCount | (0x2L << 32))));

        var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
        lowLevelFunctions.put(className.replace('/', '.') + "#run", entryBytecode);
        gen.addFunction("run", entryBytecode, Type.LONG_TYPE, commandSourceStackType, fieldStorage.getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND));


        if(!macroInstructions.isEmpty()) {
            var writer = new CodeWriter();

            var staticCommandSourceStack2 = writer.createRegister();
            writer.setValue(staticCommandSourceStack2, new BytecodeValue.Parameter(0, commandSourceStackType));
            HashMap<String, BytecodeValue> macroLocations = new HashMap<>();
            int i = 1;
            for(var macro : macroInputs) {
                var macroRegister = writer.createRegister();
                macroLocations.put(macro, macroRegister);
                writer.setValue(macroRegister, new BytecodeValue.Parameter(i, Type.getType(String.class)));
                i++;
            }
            new EmitterContext(fieldStorage, writer, compiledFunctions, new HashMap<>(Map.of("CommandSourceStack", staticCommandSourceStack2)), macroLocations, instructions).emitForEach();
            Collections.reverse(macroInputs);

            writer.returnValue(new BytecodeValue.LongValue((commandCount | (0x2L << 32))));


            var macroedBytecode = BytecodeOptimizer.optimizeInstructions(writer.getInstructions());
            lowLevelFunctions.put(className.replace('/', '.') + "#runInstanced", macroedBytecode);
            var funcParams = new Type[1 + macroInputs.size()];
            funcParams[0] = commandSourceStackType;
            Arrays.fill(funcParams, 1, funcParams.length, Type.getType(String.class));
            gen.addFunction("runInstanced", macroedBytecode, Type.LONG_TYPE, funcParams);
        }

        return gen;
    }

}
