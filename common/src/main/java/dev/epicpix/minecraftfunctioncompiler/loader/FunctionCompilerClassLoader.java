package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.CommonConstants;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.*;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidator;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.GraphGenerator;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import dev.epicpix.minecraftfunctioncompiler.reporter.CodeGenerationMetrics;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCountsData;
import dev.epicpix.minecraftfunctioncompiler.reporter.TimingData;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.*;

public abstract class FunctionCompilerClassLoader<TResourceLocation, TCommandSourceStack, TCompoundTag> extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public interface SourceFileProvider {
        Map<DataLocation, List<String>> mcfc$getSourceFiles();
    }

    private final HashMap<TResourceLocation, MethodHandle> handles = new HashMap<>();

    public final ClassLoader parent;
    private final Path generatedDir;
    private final Class<?> commandSourceStackClass;
    private final Object minecraftServer;
    private final HashMap<DataLocation, List<String>> sourceFiles;

    private State state = State.NOTHING_LOADED;

    public enum State {
        FAILED,
        NOTHING_LOADED,
        PARSING,
        COMPILING,
        LOADING,
        READY_OFF,
        READY_ON,
        EXECUTION_FAILED,
    }

    public FunctionCompilerClassLoader(ClassLoader parent, Path generatedDir, Class<?> commandSourceStackClass, Object minecraftServer, Map<DataLocation, List<String>> sourceFiles) {
        super(parent);
        this.parent = parent;
        this.generatedDir = generatedDir;
        this.commandSourceStackClass = commandSourceStackClass;
        this.minecraftServer = minecraftServer;
        this.sourceFiles = new HashMap<>(sourceFiles);
    }

    protected abstract void setHandle(DataLocation key, MethodHandle handle);

    public void registerFunction(Class<?> clazz, DataLocation key, CodeFieldStorage fieldStorage) {
        try {
            setHandle(key, MethodHandles.lookup().findStatic(clazz, "run", MethodType.methodType(long.class, commandSourceStackClass, Class.forName(fieldStorage.getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).getClassName()))));
        } catch(NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void resetHandles() {
        handles.clear();
    }

    public final boolean isUseReady() {
        return state == State.READY_ON;
    }

    public final State getState() {
        return state;
    }

    private void setState(State newState) {
        state = newState;
    }

    public void tryOff() {
        if(state == State.READY_ON || state == State.EXECUTION_FAILED) {
            setState(State.READY_OFF);
        }
    }

    public void tryOn() {
        if(state == State.READY_OFF || state == State.EXECUTION_FAILED) {
            setState(State.READY_ON);
        }
    }

    public int loadedFunctionCount() {
        return handles.size();
    }

    public int totalFunctionCount() {
        return sourceFiles.size();
    }

    private Thread backgroundThread;

    protected boolean compileInBackground(Runnable run) {
        if(backgroundThread == Thread.currentThread()) return false;
        if(state != State.NOTHING_LOADED) return true;
        setState(State.PARSING);
        backgroundThread = new Thread(run);
        backgroundThread.setName("Function Compiler");
        backgroundThread.setUncaughtExceptionHandler((t, e) -> {
            setState(State.FAILED);
            LOGGER.error("Compiler Thread threw an uncaught exception", e);
        });
        backgroundThread.start();
        return true;
    }

    private HashMap<DataLocation, List<String>> loadFunctionSources() {
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

    private HashMap<DataLocation, List<CommandParams>> parseFunctions(HashMap<DataLocation, List<String>> functionSources, IArgumentLookup lookup, ArrayList<CommandError> commandErrors) {
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

    private HashMap<DataLocation, List<Instruction>> compileFunctionsHighLevel(HashMap<DataLocation, List<CommandParams>> definedFunctions, ArrayList<CommandError> commandErrors, int[] instructionCounts) {
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        for(var func : definedFunctions.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var compiled = CommandCompiler.compile(value);
                instructionCounts[0] += compiled.size();
                var optimized = Optimizer.optimize(compiled);
                instructionCounts[1] += optimized.size();
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

    private HashMap<DataLocation, Class<?>> loadFunctions(HashMap<DataLocation, List<String>> functionSources, HashMap<DataLocation, List<CommandParams>> definedFunctions, HashMap<DataLocation, List<Instruction>> compiledFunctions, CodeFieldStorage fieldStorage, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions, int[] totalClassFileSize) {
        var loadedFunctions = new HashMap<DataLocation, Class<?>>();
        var compiledFunctionSet = new HashSet<>(compiledFunctions.keySet());
        for(var key : compiledFunctions.keySet()) {
            try {
                loadedFunctions.put(key, defineFunction(key, fieldStorage, compiledFunctions.get(key), definedFunctions.get(key).size(), compiledFunctionSet, lowLevelFunctions, totalClassFileSize));
            }catch(Throwable e) {
                LOGGER.error("Failed to load function ({}), skipping function compilation", key, e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithCommands("define_function", e, functionSources.get(key));
                FunctionCompilerReporter.sendReports();
                return null;
            }
        }
        return loadedFunctions;
    }

    private boolean loadHandles(HashMap<DataLocation, List<Instruction>> compiledFunctions, HashMap<DataLocation, Class<?>> loadedFunctions, CodeFieldStorage fieldStorage) {
        for(var key : compiledFunctions.keySet()) {
            try {
                registerFunction(loadedFunctions.get(key), key, fieldStorage);
            }catch(Throwable e) {
                LOGGER.error("Failed to create function handle ({}), clearing data", key, e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                resetHandles();
                if(DEBUG) {
                    LOGGER.info("Instruction dump:");
                    Instruction.printInstructions(compiledFunctions.get(key), LOGGER::info);
                }
                return false;
            }
        }
        return true;
    }

    public void loadFunctionClasses(IArgumentLookup lookup, CodeFieldStorage fieldStorage, Object serverFunctionManager) {
        ArrayList<CommandError> commandErrors = new ArrayList<>();

        if(DEBUG && ALLOW_EXTERNAL_DATA_ACCESS) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                var registries = DataRegistries.extractRegistries();
                var registriesStr = new GsonBuilder().setPrettyPrinting().create().toJson(registries);
                Files.writeString(generatedDir.resolve("registries.json"), registriesStr);
                LOGGER.info("Wrote {} lines into registries.json", registriesStr.lines().count());
            }catch(Exception ignored) {}
        }

        var startParse = System.nanoTime();
        var functionSources = loadFunctionSources();
        var definedFunctions = parseFunctions(functionSources, lookup, commandErrors);
        var parseTime = (System.nanoTime()-startParse)/1000000000d;

        setState(State.COMPILING);
        LOGGER.info("Compiling functions");
        var startCompile = System.nanoTime();
        var highLevelInstructionCounts = new int[2];
        var compiledFunctions = compileFunctionsHighLevel(definedFunctions, commandErrors, highLevelInstructionCounts);
        var compileTime = (System.nanoTime()-startCompile)/1000000000d;
        if(DEBUG) {
            StringBuilder instructionDump = new StringBuilder();
            var entries = new ArrayList<>(compiledFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            for(var entry : entries) {
                instructionDump.append("┌──\n");
                instructionDump.append("│ function ").append(entry.getKey()).append("\n");
                var commandCount = definedFunctions.get(entry.getKey()).size();
                instructionDump.append("│ ┣━ ").append(commandCount).append(" command").append(commandCount != 1 ? "s" : "").append("\n");
                var instructionCount = compiledFunctions.get(entry.getKey()).size();
                instructionDump.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append(" (around ").append(String.format("%.02f", commandCount == 0 ? 0d : (double)instructionCount / commandCount)).append(" instructions per command)").append("\n");
                instructionDump.append("│\n");
                Instruction.printInstructions(entry.getValue(), line -> instructionDump.append("│ ").append(line).append("\n"));
                instructionDump.append("└──\n");
                instructionDump.append("\n");
            }
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                String instructionDumpStr = instructionDump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_high_level.txt"), instructionDumpStr);
                LOGGER.info("Wrote {} lines into instruction_dump_high_level.txt", instructionDumpStr.lines().count());
            }catch(Exception ignored) {}
        }

        setState(State.LOADING);
        HashMap<String, List<BytecodeInstruction>> lowLevelFunctions = new HashMap<>();
        var startLoader = System.nanoTime();
        var totalClassFileSize = new int[2];
        var loadedFunctions = loadFunctions(functionSources, definedFunctions, compiledFunctions, fieldStorage, lowLevelFunctions, totalClassFileSize);
        if(loadedFunctions == null) return;

        try {
            loadData(fieldStorage, serverFunctionManager, lowLevelFunctions, totalClassFileSize);
        }catch(Throwable e) {
            LOGGER.error("Failed to load data", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }
        var classLoadTime = (System.nanoTime()-startLoader)/1000000000d;

        if(DEBUG) {
            StringBuilder instructionDump = new StringBuilder();
            var entries = new ArrayList<>(lowLevelFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            for(var entry : entries) {
                instructionDump.append("┌──\n");
                instructionDump.append("│ function ").append(entry.getKey()).append("\n");
                var instructions = entry.getValue();
                var instructionCount = instructions.size();
                instructionDump.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append("\n");
                instructionDump.append("│\n");
                for(var instr : instructions) {
                    instructionDump.append("│ ").append(BytecodeFormatter.formatInstruction(instr)).append("\n");
                }
                instructionDump.append("└──\n");
                instructionDump.append("\n");
            }
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                String instructionDumpStr = instructionDump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.txt"), instructionDumpStr);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.txt", instructionDumpStr.lines().count());
            }catch(Exception ignored) {}
        }
        if(DEBUG_GENERATE_GRAPH) {
            var startGraph = System.nanoTime();
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                GraphGenerator.generateGraphs(compiledFunctions, generatedDir);
                if(DEBUG) {
                    BytecodeGraphGenerator.generateBytecodeGraphs(lowLevelFunctions, generatedDir);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("Generated graphs in {}ms", (System.nanoTime() - startGraph) / 1000000);
        }

        var startHandles = System.nanoTime();
        if(!loadHandles(compiledFunctions, loadedFunctions, fieldStorage)) return;
        var handleLoadTime = (System.nanoTime()-startHandles)/1000000000d;

        var functionCountsData = new FunctionCountsData(sourceFiles.size(), definedFunctions.size(), compiledFunctions.size());
        var timingData = new TimingData(parseTime, compileTime, classLoadTime, handleLoadTime);
        var codeGenMetrics = new CodeGenerationMetrics(
            definedFunctions.values().stream().mapToInt(List::size).sum(),
            highLevelInstructionCounts[0],
            highLevelInstructionCounts[1],
            lowLevelFunctions.values().stream().mapToInt(List::size).sum(),
            totalClassFileSize[0],
            totalClassFileSize[1]
        );
        FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, codeGenMetrics, commandErrors);

        FunctionCompilerReporter.sendReports();
        if(AUTO_SWITCH_FUNCTIONS) {
            LOGGER.info("Switched over to running compiled functions");
            setState(State.READY_ON);
        }else {
            LOGGER.info("Compiled functions are ready to be enabled");
            setState(State.READY_OFF);
        }
    }

    protected abstract BytecodeValue nbtTagToString(EmitterContext context, String key, BytecodeLabel errorLabel);

    private void writeMacroInstanceCreation(String className, BytecodeLabel errorLabel, EmitterContext context, List<Instruction> macroInstructions, List<String> macroInputs) {
        context.codeWriter().conditionalJump(JumpCondition.EQ, context.getStatic("FunctionCompound"), new BytecodeValue.NullValue(), errorLabel);

        HashMap<String, HashSet<MacroValidator>> generatedValidators = new HashMap<>();

        for(var macroInstr : macroInstructions) {
            var macros = macroInstr.<MacroTemplate<String>>get("macroTemplate").extractMacros();
            for(var macro : macros) {
                var generated = generatedValidators.computeIfAbsent(macro.source(), k -> new HashSet<>());
                if(generated.containsAll(macro.validators())) {
                    continue;
                }
                var value = nbtTagToString(context, macro.source(), errorLabel);
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
            arguments.add(nbtTagToString(context, macro, null));
        }

        var target = new InstructionTarget.FunctionTarget(className, "runInstanced",  "(" + commandSourceStackClass.descriptorString() + "Ljava/lang/String;".repeat(macroInputs.size()) + ")J", true, false, false, true);
        context.codeWriter().returnValue(context.codeWriter().writeTarget(target, arguments));
    }

    private Class<?> defineFunction(DataLocation funcId, CodeFieldStorage fieldStorage, List<Instruction> instructions, int commandCount, Set<DataLocation> compiledFunctions, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions, int[] totalClassFileSize) {
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
        entryWriter.setValue(staticCommandSourceStack, new BytecodeValue.Parameter(0, Type.getType(commandSourceStackClass)));
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
            writeMacroInstanceCreation(className, errorLabel, entryContext, macroInstructions, macroInputs);
            entryWriter.label(errorLabel);
        }
        entryContext.emitForEach();
        entryWriter.returnValue(new BytecodeValue.LongValue((commandCount | (0x2L << 32))));

        var entryBytecode = BytecodeOptimizer.optimizeInstructions(entryWriter.getInstructions());
        lowLevelFunctions.put(className.replace('/', '.') + "#run", entryBytecode);
        gen.addFunction("run", entryBytecode, Type.LONG_TYPE, Type.getType(commandSourceStackClass), fieldStorage.getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND));


        if(!macroInstructions.isEmpty()) {
            var writer = new CodeWriter();

            var staticCommandSourceStack2 = writer.createRegister();
            writer.setValue(staticCommandSourceStack2, new BytecodeValue.Parameter(0, Type.getType(commandSourceStackClass)));
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
            funcParams[0] = Type.getType(commandSourceStackClass);
            Arrays.fill(funcParams, 1, funcParams.length, Type.getType(String.class));
            gen.addFunction("runInstanced", macroedBytecode, Type.LONG_TYPE, funcParams);
        }

        byte[] bytes = gen.finish();
        totalClassFileSize[0] += bytes.length;
        return defineClass(className.replace('/', '.'), bytes);
    }

    private Class<?> loadData(CodeFieldStorage fieldStorage, Object serverFunctionManager, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, int[] totalClassFileSize) throws Throwable {
        var dataClassBytes = fieldStorage.generate(lowLevelInstructions);
        totalClassFileSize[1] += dataClassBytes.length;
        var defined = defineClass(DATA_CLASS.replace('/', '.'), dataClassBytes);
        defined.getDeclaredMethod("load", Class.forName(fieldStorage.minecraftServerType.getClassName()), Class.forName(fieldStorage.serverFunctionManagerType.getClassName())).invoke(null, minecraftServer, serverFunctionManager);
        return defined;
    }

    private Class<?> defineClass(String name, byte[] data) {
        if(CommonConstants.DEBUG) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                Files.write(generatedDir.resolve(name + ".class"), data);
            }catch(Exception ignored) {}
        }
        return defineClass(name, data, 0, data.length);
    }

    public boolean hasFunctionLoaded(TResourceLocation location) {
        return handles.containsKey(location);
    }

    public void setHandle(TResourceLocation key, MethodHandle handle) {
        handles.put(key, handle);
    }

    public long runFunction(TResourceLocation location, TCommandSourceStack stack, TCompoundTag compound) {
        try {
            return (long) handles.get(location).invoke(stack, compound);
        } catch (Throwable e) {
            LOGGER.error("Failed to run function ({}), disabling mcfc, report this bug to authors", location, e);
            setState(State.EXECUTION_FAILED);
            return 3L << 32;
        }
    }
}
