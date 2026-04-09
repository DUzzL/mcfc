package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
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
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.LengauerTarjan;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.UNSPECIFIED_COMMAND_ERROR;

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

    private boolean loadHandles(HashMap<DataLocation, Class<?>> loadedFunctions, CompilationParams params) {
        for(var key : loadedFunctions.keySet()) {
            try {
                registerFunction(loadedFunctions.get(key), key, params);
            } catch(Throwable e) {
                if(e instanceof DiagnosticException de) {
                    diagnostics.addDiagnostic(de.toDiagnostic());
                }
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_FAILED, "Failed to create function handle (" + key + ")", null, key, null, e));
                LOGGER.error("Failed to create function handle ({}), clearing data", key, e);
                setState(State.FAILED);
                // IL not available in streaming mode — pass empty list for telemetry
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, List.of());
                FunctionCompilerReporter.sendReports();
                resetHandles();
                return false;
            }
        }
        return true;
    }

    public void loadFunctionClasses(IArgumentLookup lookup, CompilationParams params, Object serverFunctionManager, boolean autoEnable) {
        if(ALLOW_EXTERNAL_DATA_ACCESS && GENERATE_REGISTRY_FILE) {
            try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
            try {
                var registries = DataRegistries.extractRegistries();
                var registriesStr = new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(registries);
                Files.writeString(generatedDir.resolve("registries.json"), registriesStr);
                LOGGER.info("Wrote {} lines into registries.json", registriesStr.lines().count());
            } catch(Exception ignored) {}
        }

        // ── Parse phase ───────────────────────────────────────────────────────
        var startParse = System.nanoTime();
        var functionSources = FunctionCodeGenerator.loadFunctionSources(sourceFiles);
        var definedFunctions = FunctionCodeGenerator.parseFunctions(functionSources, lookup, diagnostics);
        var parseTime = (System.nanoTime() - startParse) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Parsed functions ({}->{}) in {}ms", sourceFiles.size(), definedFunctions.size(), (int)(parseTime * 1000));

        // functionSources content is no longer needed — help the GC early
        functionSources.clear();

        int parsedFunctionCount = definedFunctions.size();
        int compiledCommandCount = definedFunctions.values().stream().mapToInt(List::size).sum();

        setState(State.COMPILING);
        LOGGER.info("Compiling functions");

        // ── Combined compile + optimize + generate class bytes ────────────────
        //
        // OLD pipeline: compileFunctionsHighLevel -> optimizeFunctionsHighLevel -> loadFunctions
        //   Peak memory = definedFunctions (commands) + compiledFunctions (IL) +
        //                 optimizedFunctions (IL) + lowLevelFunctions (bytecode) +
        //                 generatedBytes (class files) — all alive at the same time.
        //
        // NEW pipeline: one parallelStream pass over definedFunctions that compiles,
        //   optimizes, and generates class bytes per function before moving on.
        //   Peak memory = lowLevelFunctions (bytecode) + generatedBytes (class files).
        //   The two large IL maps (compiled + optimized) are never accumulated.
        //   Each function's IL lives only for the duration of its stream element.
        //
        var startCompile = System.nanoTime();

        var lowLevelFunctions     = new ConcurrentHashMap<String, List<BytecodeInstruction>>();
        var totalFunctionFileSize = new AtomicInteger(0);
        var totalDataFileSize     = new AtomicInteger(0);
        var preOptInstrCount      = new AtomicInteger(0);
        var postOptInstrCount     = new AtomicInteger(0);
        var successCount          = new AtomicInteger(0);

        // Debug maps — only allocated when DEBUG_DUMP_INSTRUCTIONS is true.
        // DEBUG_DUMP_INSTRUCTIONS is a compile-time false constant in production,
        // so the ternary is eliminated and both vars become null literals by javac.
        ConcurrentHashMap<DataLocation, List<Instruction>> debugCompiledMap  = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;
        ConcurrentHashMap<DataLocation, List<Instruction>> debugOptimizedMap = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;

        var compiledFunctionSet = new HashSet<>(definedFunctions.keySet());
        var generatedBytes      = new ConcurrentHashMap<DataLocation, byte[]>();

        definedFunctions.entrySet().parallelStream().forEach(entry -> {
            var key      = entry.getKey();
            var commands = entry.getValue();

            // 1) Compile to high-level IL
            List<Instruction> compiled;
            try {
                compiled = CommandCompiler.compile(key, commands);
            } catch(DiagnosticException e) {
                LOGGER.warn("Failed to compile function {} / {}", key, e.getMessage());
                diagnostics.addDiagnosticSafe(e.toDiagnostic());
                return;
            } catch(Throwable e) {
                LOGGER.error("Failed to compile function {}", key, e);
                diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
                return;
            }
            preOptInstrCount.addAndGet(compiled.size());
            if(DEBUG_DUMP_INSTRUCTIONS) debugCompiledMap.put(key, Instructions.addStaticRequirements(compiled));

            // 2) Optimize IL (LengauerTarjan is stateful — create per-thread)
            List<Instruction> optimized;
            try {
                optimized = Optimizer.optimize(compiled, new LengauerTarjan());
                optimized = Instructions.addStaticRequirements(optimized);
            } catch(DiagnosticException e) {
                LOGGER.warn("Failed to optimize function {} / {}", key, e.getMessage());
                diagnostics.addDiagnosticSafe(e.toDiagnostic());
                return;
            } catch(Throwable e) {
                LOGGER.error("Failed to optimize function {}", key, e);
                diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, e.getMessage(), e));
                return;
            }
            postOptInstrCount.addAndGet(optimized.size());
            successCount.incrementAndGet();
            if(DEBUG_DUMP_INSTRUCTIONS) debugOptimizedMap.put(key, optimized);

            // 3) Generate class bytes — IL is referenced here, then released by the GC
            try {
                var bytes = FunctionCodeGenerator.generateFunctionClass(params, key, optimized, compiledFunctionSet, lowLevelFunctions);
                totalFunctionFileSize.addAndGet(bytes.length);
                generatedBytes.put(key, bytes);
            } catch(DiagnosticException e) {
                diagnostics.addDiagnosticSafe(e.toDiagnostic());
                LOGGER.error("Failed to generate class for function ({}), skipping", key, e);
            } catch(Throwable e) {
                diagnostics.addDiagnosticSafe(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_CLASS_LOAD_FAILED, "Failed to load function (" + key + ")", null, key, null, e));
                LOGGER.error("Failed to load function ({}), skipping", key, e);
            }
        });

        // Parsed command data is no longer referenced — release it
        definedFunctions.clear();

        var compileTime = (System.nanoTime() - startCompile) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Compiled+optimized+generated {} functions in {}ms", generatedBytes.size(), (int)(compileTime * 1000));

        // High-level instruction dump (compile-time dead code in production)
        if(DEBUG_DUMP_INSTRUCTIONS) {
            for(boolean postOpt : new boolean[]{true, false}) {
                var map     = postOpt ? debugOptimizedMap : debugCompiledMap;
                var suffix  = postOpt ? "high_level" : "high_level_no_opt";
                var dump    = new StringBuilder();
                var json    = new JsonObject();
                var entries = new ArrayList<>(map.entrySet());
                entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
                for(var e : entries) {
                    var instrList = e.getValue();
                    dump.append("┌──\n│ function ").append(e.getKey()).append("\n");
                    dump.append("│ ┗━ ").append(instrList.size()).append(" instruction(s)\n│\n");
                    Instruction.printInstructions(instrList, line -> dump.append("│ ┗━ ").append(line).append("\n"));
                    dump.append("└──\n\n");
                    json.add(e.getKey().toString(), Instruction.generateInstructionsJson(instrList));
                }
                try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
                try {
                    String txt = dump.toString();
                    Files.writeString(generatedDir.resolve("instruction_dump_" + suffix + ".txt"), txt);
                    LOGGER.info("Wrote {} lines into instruction_dump_{}.txt", txt.lines().count(), suffix);
                    String jsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(json);
                    Files.writeString(generatedDir.resolve("instruction_dump_" + suffix + ".json"), jsonStr);
                    LOGGER.info("Wrote {} lines into instruction_dump_{}.json", jsonStr.lines().count(), suffix);
                } catch(Exception ignored) {}
            }
        }

        // ── Load: defineClass sequentially ───────────────────────────────────
        LOGGER.info("Loading functions");
        setState(State.LOADING);
        var startLoader = System.nanoTime();

        var loadedFunctions = new HashMap<DataLocation, Class<?>>();
        for(var entry : generatedBytes.entrySet()) {
            var key  = entry.getKey();
            var name = ("mcfc/function/" + key.namespace() + "/" + key.path()).replace('.', '$').replace('/', '.');
            loadedFunctions.put(key, defineClass(name, entry.getValue()));
        }
        generatedBytes.clear(); // class bytes no longer needed

        // ── Data class ───────────────────────────────────────────────────────
        try {
            var dataClassBytes = params.codeFieldStorage().generate(new HashMap<>(lowLevelFunctions));
            totalDataFileSize.addAndGet(dataClassBytes.length);
            var desc    = DATA_CLASS.descriptorString();
            var defined = defineClass(desc.substring(1, desc.length() - 1).replace('/', '.'), dataClassBytes);
            var lk      = MethodHandles.lookup();
            defined.getDeclaredMethod("load",
                params.codeFieldStorage().minecraftServerType.resolveConstantDesc(lk),
                params.codeFieldStorage().serverFunctionManagerType.resolveConstantDesc(lk)
            ).invoke(null, minecraftServer, serverFunctionManager);
            resultField = lk.findStaticSetter(defined, RUN_RESULT_FIELD, long.class);
        } catch(Throwable e) {
            if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
            LOGGER.error("Failed to load data", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }

        var classLoadTime = (System.nanoTime() - startLoader) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded classes in {}ms", (int)(classLoadTime * 1000));

        // Low-level instruction dump (compile-time dead code in production)
        if(DEBUG_DUMP_INSTRUCTIONS) {
            var dump    = new StringBuilder();
            var json    = new JsonObject();
            var entries = new ArrayList<>(lowLevelFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            for(var e : entries) {
                dump.append("┌──\n│ function ").append(e.getKey()).append("\n");
                var instrList = e.getValue();
                dump.append("│ ┗━ ").append(instrList.size()).append(" instruction(s)\n│\n");
                var instrJson = new JsonArray();
                for(var instr : instrList) {
                    dump.append("│ ").append(BytecodeFormatter.formatInstruction(instr)).append("\n");
                    instrJson.add(BytecodeFormatter.instructionToJson(instr));
                }
                json.add(e.getKey(), instrJson);
                dump.append("└──\n\n");
            }
            try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
            try {
                String txt = dump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.txt"), txt);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.txt", txt.lines().count());
                String jsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(json);
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.json"), jsonStr);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.json", jsonStr.lines().count());
            } catch(Exception ignored) {}
        }

        if(DEBUG_GENERATE_GRAPH) {
            try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
            if(DEBUG_DUMP_INSTRUCTIONS) {
                // Optimized IL is only retained in debugOptimizedMap when both flags are set
                GraphGenerator.generateGraphs(new HashMap<>(debugOptimizedMap), generatedDir);
            }
            BytecodeGraphGenerator.generateBytecodeGraphs(lowLevelFunctions, generatedDir);
        }

        // ── Handle registration ───────────────────────────────────────────────
        var startHandles = System.nanoTime();
        if(!loadHandles(loadedFunctions, params)) return;
        var handleLoadTime = (System.nanoTime() - startHandles) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded handles in {}ms", (int)(handleLoadTime * 1000));

        // ── Metrics & telemetry ───────────────────────────────────────────────
        var functionCountsData = new FunctionCountsData(sourceFiles.size(), parsedFunctionCount, successCount.get(), loadedFunctions.size());
        var timingData         = new TimingData(parseTime, compileTime, 0d, classLoadTime, handleLoadTime);
        var codeGenMetrics     = new CodeGenerationMetrics(
            compiledCommandCount,
            preOptInstrCount.get(),
            postOptInstrCount.get(),
            lowLevelFunctions.values().stream().mapToInt(List::size).sum(),
            totalFunctionFileSize.get(),
            totalDataFileSize.get()
        );
        FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, codeGenMetrics, diagnostics.getDiagnostics());
        FunctionCompilerReporter.sendReports();

        if(autoEnable) {
            LOGGER.info("Switched over to running compiled functions");
            setState(State.READY_ON);
        } else {
            LOGGER.info("Compiled functions are ready to be enabled");
            setState(State.READY_OFF);
        }
    }

    private Class<?> defineClass(String name, byte[] data) {
        if(DEBUG_DUMP_CLASS_FILES) {
            try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
            try { Files.write(generatedDir.resolve(name + ".class"), data); } catch(Exception ignored) {}
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
        } catch(Throwable e) {
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
