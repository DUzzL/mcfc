package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticCollector;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.GraphGenerator;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeFormatter;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeGraphGenerator;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import dev.epicpix.minecraftfunctioncompiler.reporter.CodeGenerationMetrics;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCountsData;
import dev.epicpix.minecraftfunctioncompiler.reporter.TimingData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ALLOW_EXTERNAL_DATA_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_DUMP_CLASS_FILES;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_DUMP_INSTRUCTIONS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_GENERATE_GRAPH;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_PRINT_TIMINGS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.GENERATE_REGISTRY_FILE;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.RUN_RESULT_FIELD;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMPILER_UNCAUGHT_EXCEPTION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_CLASS_LOAD_FAILED;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_EXECUTION_FAILED;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_LOAD_FAILED;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_LOAD_TOO_BIG;

public abstract class FunctionCompilerClassLoader<TMinecraftServer, TIdentifier, TCommandSourceStack, TCompoundTag> extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public interface SourceFileProvider {
        default Map<DataLocation, List<String>> mcfc$getSourceFiles() {
            throw new UnsupportedOperationException();
        }
    }

    private final HashMap<TIdentifier, MethodHandle> handles = new HashMap<>();

    public final ClassLoader parent;
    public final TMinecraftServer minecraftServer;
    private final Path generatedDir;
    private final HashMap<DataLocation, List<String>> sourceFiles;
    private final DiagnosticCollector diagnostics = new DiagnosticCollector();

    private State state = State.NOTHING_LOADED;
    private @Nullable MethodHandle resultField;

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

    public FunctionCompilerClassLoader(ClassLoader parent, Path generatedDir, TMinecraftServer minecraftServer, Map<DataLocation, List<String>> sourceFiles) {
        super(parent);
        this.parent = parent;
        this.generatedDir = generatedDir;
        this.minecraftServer = minecraftServer;
        this.sourceFiles = new HashMap<>(sourceFiles);
    }

    protected abstract void setHandle(DataLocation key, MethodHandle handle);

    public void registerFunction(Class<?> clazz, DataLocation key, CompilationParams params) {
        var lookup = MethodHandles.lookup();
        try {
            setHandle(key, lookup.findStatic(clazz, "run", MethodType.methodType(long.class, params.commandSourceStackType().resolveConstantDesc(lookup), params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).resolveConstantDesc(lookup))));
        } catch(ReflectiveOperationException e) {
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

    @Nullable
    private Thread backgroundThread;

    protected boolean compileInBackground(Runnable run) {
        if(backgroundThread == Thread.currentThread()) return false;
        if(state != State.NOTHING_LOADED) return true;
        setState(State.PARSING);
        backgroundThread = new Thread(run);
        backgroundThread.setName("Function Compiler");
        backgroundThread.setUncaughtExceptionHandler((t, e) -> {
            if(e instanceof DiagnosticException de) {
                diagnostics.addDiagnostic(de.toDiagnostic());
            }
            diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, COMPILER_UNCAUGHT_EXCEPTION, "Uncaught exception caught in compilation thread", null, null, null, e));
            setState(State.FAILED);
            LOGGER.error("Compiler Thread threw an uncaught exception", e);
        });
        backgroundThread.start();
        return true;
    }

    @Nullable
    private HashMap<DataLocation, Class<?>> loadFunctions(CompilationParams params, HashMap<DataLocation, List<String>> functionSources, HashMap<DataLocation, List<Instruction>> compiledFunctions, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions, int[] totalClassFileSize) {
        var loadedFunctions = new HashMap<DataLocation, Class<?>>();
        var compiledFunctionSet = new HashSet<>(compiledFunctions.keySet());
        for(var key : compiledFunctions.keySet()) {
            try {
                loadedFunctions.put(key, defineFunction(params, key, compiledFunctions.get(key), compiledFunctionSet, lowLevelFunctions, totalClassFileSize));
            }catch(Throwable e) {
                if(e instanceof DiagnosticException de) {
                    diagnostics.addDiagnostic(de.toDiagnostic());
                }
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_CLASS_LOAD_FAILED, "Failed to load function (" + key + ")", null, key, null, e));
                LOGGER.error("Failed to load function ({}), skipping function compilation", key, e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithCommands("define_function", e, functionSources.get(key));
                FunctionCompilerReporter.sendReports();
                return null;
            }
        }
        return loadedFunctions;
    }

    private boolean loadHandles(HashMap<DataLocation, List<Instruction>> compiledFunctions, HashMap<DataLocation, Class<?>> loadedFunctions, CompilationParams params) {
        for(var key : compiledFunctions.keySet()) {
            try {
                registerFunction(loadedFunctions.get(key), key, params);
            }catch(Throwable e) {
                if(e instanceof DiagnosticException de) {
                    diagnostics.addDiagnostic(de.toDiagnostic());
                }
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_FAILED, "Failed to create function handle (" + key + ")", null, key, null, e));
                LOGGER.error("Failed to create function handle ({}), clearing data", key, e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                resetHandles();
                if(DEBUG_DUMP_INSTRUCTIONS) {
                    LOGGER.info("Instruction dump:");
                    Instruction.printInstructions(compiledFunctions.get(key), x -> LOGGER.info("┗━ {}", x));
                }
                return false;
            }
        }
        return true;
    }

    public void loadFunctionClasses(IArgumentLookup lookup, CompilationParams params, Object serverFunctionManager, boolean autoEnable) {
        if(ALLOW_EXTERNAL_DATA_ACCESS && GENERATE_REGISTRY_FILE) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                var registries = DataRegistries.extractRegistries();
                var registriesStr = new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(registries);
                Files.writeString(generatedDir.resolve("registries.json"), registriesStr);
                LOGGER.info("Wrote {} lines into registries.json", registriesStr.lines().count());
            }catch(Exception ignored) {}
        }

        var startParse = System.nanoTime();
        var functionSources = FunctionCodeGenerator.loadFunctionSources(sourceFiles);
        var definedFunctions = FunctionCodeGenerator.parseFunctions(functionSources, lookup, diagnostics);
        var parseTime = (System.nanoTime()-startParse)/1000000000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Parsed functions ({}->{}) in {}ms", sourceFiles.size(), definedFunctions.size(), (int) (parseTime*1000));

        setState(State.COMPILING);
        LOGGER.info("Compiling functions");
        var startCompile = System.nanoTime();
        var highLevelInstructionCounts = new int[2];
        var compiledFunctions = FunctionCodeGenerator.compileFunctionsHighLevel(definedFunctions, diagnostics, highLevelInstructionCounts, false);
        var compileTime = (System.nanoTime()-startCompile)/1000000000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Compiled functions to high level ({}->{}) in {}ms", definedFunctions.size(), compiledFunctions.size(), (int) (compileTime*1000));
        LOGGER.info("Optimizing functions");
        var startOptimize = System.nanoTime();
        var optimizedFunctions = FunctionCodeGenerator.optimizeFunctionsHighLevel(compiledFunctions, diagnostics, highLevelInstructionCounts, true);
        var optimizeTime = (System.nanoTime()-startOptimize)/1000000000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Optimized high level instructions in {}ms", (int) (optimizeTime * 1000));
        if(DEBUG_DUMP_INSTRUCTIONS) {
            StringBuilder instructionDump = new StringBuilder();
            var entries = new ArrayList<>(optimizedFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            var instructionsJson = new JsonObject();
            for(var entry : entries) {
                instructionDump.append("┌──\n");
                instructionDump.append("│ function ").append(entry.getKey()).append("\n");
                var commandCount = definedFunctions.get(entry.getKey()).size();
                instructionDump.append("│ ┣━ ").append(commandCount).append(" command").append(commandCount != 1 ? "s" : "").append("\n");
                var instructions = entry.getValue();
                var instructionCount = instructions.size();
                instructionDump.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append(" (around ").append(String.format("%.02f", commandCount == 0 ? 0d : (double)instructionCount / commandCount)).append(" instructions per command)").append("\n");
                instructionDump.append("│\n");
                Instruction.printInstructions(instructions, line -> instructionDump.append("│ ┗━ ").append(line).append("\n"));
                instructionDump.append("└──\n");
                instructionDump.append("\n");
                instructionsJson.add(entry.getKey().toString(), Instruction.generateInstructionsJson(instructions));
            }
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                String instructionDumpStr = instructionDump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_high_level.txt"), instructionDumpStr);
                LOGGER.info("Wrote {} lines into instruction_dump_high_level.txt", instructionDumpStr.lines().count());
                String instructionDumpJsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(instructionsJson);
                Files.writeString(generatedDir.resolve("instruction_dump_high_level.json"), instructionDumpJsonStr);
                LOGGER.info("Wrote {} lines into instruction_dump_high_level.json", instructionDumpJsonStr.lines().count());
            }catch(Exception ignored) {}

            var instructionDump2 = new StringBuilder();
            entries = new ArrayList<>(compiledFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            instructionsJson = new JsonObject();
            for(var entry : entries) {
                instructionDump2.append("┌──\n");
                instructionDump2.append("│ function ").append(entry.getKey()).append("\n");
                var commandCount = definedFunctions.get(entry.getKey()).size();
                instructionDump2.append("│ ┣━ ").append(commandCount).append(" command").append(commandCount != 1 ? "s" : "").append("\n");
                var instructions = Instructions.addStaticRequirements(entry.getValue());
                var instructionCount = instructions.size();
                instructionDump2.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append(" (around ").append(String.format("%.02f", commandCount == 0 ? 0d : (double)instructionCount / commandCount)).append(" instructions per command)").append("\n");
                instructionDump2.append("│\n");
                Instruction.printInstructions(instructions, line -> instructionDump2.append("│ ┗━ ").append(line).append("\n"));
                instructionDump2.append("└──\n");
                instructionDump2.append("\n");
                instructionsJson.add(entry.getKey().toString(), Instruction.generateInstructionsJson(instructions));
            }
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                String instructionDumpStr = instructionDump2.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_high_level_no_opt.txt"), instructionDumpStr);
                LOGGER.info("Wrote {} lines into instruction_dump_high_level_no_opt.txt", instructionDumpStr.lines().count());
                String instructionDumpJsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(instructionsJson);
                Files.writeString(generatedDir.resolve("instruction_dump_high_level_no_opt.json"), instructionDumpJsonStr);
                LOGGER.info("Wrote {} lines into instruction_dump_high_level_no_opt.json", instructionDumpJsonStr.lines().count());
            }catch(Exception ignored) {}
        }
        LOGGER.info("Loading functions");

        setState(State.LOADING);
        HashMap<String, List<BytecodeInstruction>> lowLevelFunctions = new HashMap<>();
        var startLoader = System.nanoTime();
        var totalClassFileSize = new int[2];
        var loadableFunctions = new HashMap<DataLocation, List<Instruction>>();
        for(var entry : optimizedFunctions.entrySet()) {
            if(entry.getValue().size() > 5000) {
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_TOO_BIG, "Function has too many instructions, ignoring", null, entry.getKey(), null, null));
                LOGGER.warn("Function has too many instructions, ignoring: {}", entry.getKey());
            }else {
                loadableFunctions.put(entry.getKey(), entry.getValue());
            }
        }
        var loadedFunctions = loadFunctions(params, functionSources, loadableFunctions, lowLevelFunctions, totalClassFileSize);
        if(loadedFunctions == null) return;

        try {
            var dataClass = loadData(params.codeFieldStorage(), serverFunctionManager, lowLevelFunctions, totalClassFileSize);
            resultField = MethodHandles.lookup().findStaticSetter(dataClass, RUN_RESULT_FIELD, long.class);
        }catch(Throwable e) {
            if(e instanceof DiagnosticException de) {
                diagnostics.addDiagnostic(de.toDiagnostic());
            }
            LOGGER.error("Failed to load data", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }
        var classLoadTime = (System.nanoTime()-startLoader)/1000000000d;

        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded classes in {}ms", (int) (classLoadTime * 1000));
        if(DEBUG_DUMP_INSTRUCTIONS) {
            StringBuilder instructionDump = new StringBuilder();
            var entries = new ArrayList<>(lowLevelFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            var instructionsJson = new JsonObject();
            for(var entry : entries) {
                instructionDump.append("┌──\n");
                instructionDump.append("│ function ").append(entry.getKey()).append("\n");
                var instructions = entry.getValue();
                var instructionCount = instructions.size();
                instructionDump.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append("\n");
                instructionDump.append("│\n");
                var instrs = new JsonArray();
                for(var instr : instructions) {
                    instructionDump.append("│ ").append(BytecodeFormatter.formatInstruction(instr)).append("\n");
                    instrs.add(BytecodeFormatter.instructionToJson(instr));
                }
                instructionsJson.add(entry.getKey(), instrs);
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
                String instructionDumpJsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(instructionsJson);
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.json"), instructionDumpJsonStr);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.json", instructionDumpJsonStr.lines().count());
            }catch(Exception ignored) {}
        }
        if(DEBUG_GENERATE_GRAPH) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            GraphGenerator.generateGraphs(optimizedFunctions, generatedDir);
            BytecodeGraphGenerator.generateBytecodeGraphs(lowLevelFunctions, generatedDir);
        }

        var startHandles = System.nanoTime();
        if(!loadHandles(loadableFunctions, loadedFunctions, params)) return;
        var handleLoadTime = (System.nanoTime()-startHandles)/1000000000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded handles in {}ms", (int) (handleLoadTime*1000));

        var functionCountsData = new FunctionCountsData(sourceFiles.size(), definedFunctions.size(), optimizedFunctions.size(), loadableFunctions.size());
        var timingData = new TimingData(parseTime, compileTime, optimizeTime, classLoadTime, handleLoadTime);
        var codeGenMetrics = new CodeGenerationMetrics(
            definedFunctions.values().stream().mapToInt(List::size).sum(),
            highLevelInstructionCounts[0],
            highLevelInstructionCounts[1],
            lowLevelFunctions.values().stream().mapToInt(List::size).sum(),
            totalClassFileSize[0],
            totalClassFileSize[1]
        );
        FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, codeGenMetrics, diagnostics.getDiagnostics());

        FunctionCompilerReporter.sendReports();
        if(autoEnable) {
            LOGGER.info("Switched over to running compiled functions");
            setState(State.READY_ON);
        }else {
            LOGGER.info("Compiled functions are ready to be enabled");
            setState(State.READY_OFF);
        }
    }

    private Class<?> defineFunction(CompilationParams params, DataLocation funcId, List<Instruction> instructions, Set<DataLocation> compiledFunctions, HashMap<String, List<BytecodeInstruction>> lowLevelFunctions, int[] totalClassFileSize) {
        var bytes = FunctionCodeGenerator.generateFunctionClass(params, funcId, instructions, compiledFunctions, lowLevelFunctions);

        totalClassFileSize[0] += bytes.length;
        var name = ("mcfc/function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$').replace('/', '.');
        return defineClass(name, bytes);
    }

    private Class<?> loadData(CodeFieldStorage fieldStorage, Object serverFunctionManager, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, int[] totalClassFileSize) throws Throwable {
        var dataClassBytes = fieldStorage.generate(lowLevelInstructions);
        totalClassFileSize[1] += dataClassBytes.length;
        var desc = DATA_CLASS.descriptorString();
        var defined = defineClass(desc.substring(1, desc.length() - 1).replace('/', '.'), dataClassBytes);
        var lookup = MethodHandles.lookup();
        defined.getDeclaredMethod("load", fieldStorage.minecraftServerType.resolveConstantDesc(lookup), fieldStorage.serverFunctionManagerType.resolveConstantDesc(lookup)).invoke(null, minecraftServer, serverFunctionManager);
        return defined;
    }

    private Class<?> defineClass(String name, byte[] data) {
        if(DEBUG_DUMP_CLASS_FILES) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                Files.write(generatedDir.resolve(name + ".class"), data);
            }catch(Exception ignored) {}
        }
        return defineClass(name, data, 0, data.length);
    }

    public boolean hasFunctionLoaded(TIdentifier location) {
        return handles.containsKey(location);
    }

    public void setHandle(TIdentifier key, MethodHandle handle) {
        handles.put(key, handle);
    }

    public long runFunction(TIdentifier location, TCommandSourceStack stack, @Nullable TCompoundTag compound) {
        try {
            return (long) handles.get(location).invoke(stack, compound);
        } catch (Throwable e) {
            LOGGER.error("Failed to run function ({}), disabling mcfc, report this bug to authors", location, e);
            diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, FUNCTION_EXECUTION_FAILED, "Failed to run function (" + location + ")", null, new DataLocation(location.toString()), null, e));
            setState(State.EXECUTION_FAILED);
            return 3L << 32;
        }
    }

    public void storeResult(long value) {
        if(isUseReady()) {
            try {
                assert resultField != null;
                resultField.invoke(value);
            } catch(Throwable e) {
                LOGGER.error("Failed to store result value: {}", value, e);
            }
        }
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics.getDiagnostics();
    }
}
