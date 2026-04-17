package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.Gson;
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
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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

    // ── Reusable Gson / HexFormat instances ────────────────────────────────────
    // These are stateless and thread-safe; allocating a new Gson per call is wasteful.
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson PRETTY_GSON_WITH_NULLS = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Gson PRETTY_GSON_NO_HTML_ESCAPE = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final HexFormat HEX = HexFormat.of();

    // ── Cache constants ────────────────────────────────────────────────────────
    /** Incremented whenever the on-disk cache format changes. v2 = batch-class layout. */
    private static final String CACHE_VERSION      = "v2.1";
    private static final String CACHE_MANIFEST     = "manifest.json";
    private static final String CACHE_DATA_CLASS   = "data.class";
    private static final String CACHE_BATCH_PREFIX = "batch/";

    /**
     * Idempotent: ensures generatedDir exists. {@link Files#createDirectories}
     * is a no-op when the directory already exists, so the only failures here
     * are real I/O problems — those are logged at warn level rather than swallowed.
     */
    private void ensureGeneratedDir() {
        try {
            Files.createDirectories(generatedDir);
        } catch(Exception e) {
            LOGGER.warn("Failed to create generated dir {}", generatedDir, e);
        }
    }

    public interface SourceFileProvider {
        default Map<DataLocation, List<String>> mcfc$getSourceFiles() {
            throw new UnsupportedOperationException();
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<TIdentifier, MethodHandle> handles = new java.util.concurrent.ConcurrentHashMap<>();

    public final ClassLoader parent;
    public final TMinecraftServer minecraftServer;
    private final Path generatedDir;
    private final Path cacheDir;
    private final HashMap<DataLocation, List<String>> sourceFiles;
    private final DiagnosticCollector diagnostics = new DiagnosticCollector();

    private volatile State state = State.NOTHING_LOADED;
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
        this.cacheDir = generatedDir.resolve("cache");
        this.minecraftServer = minecraftServer;
        this.sourceFiles = new HashMap<>(sourceFiles);
    }

    /**
     * Returns a version string that identifies the current compiler build.
     * When this string changes (e.g. after a mod update), the entire cache is
     * invalidated automatically.  Override in platform-specific subclasses.
     */
    protected String getCompilerVersion() {
        return "";
    }

    protected abstract void setHandle(DataLocation key, MethodHandle handle);

    public void registerFunction(Class<?> batchClass, DataLocation key, BatchClassPlanner.PlacedFunction placement, CompilationParams params) {
        var lookup = MethodHandles.lookup();
        try {
            setHandle(key, lookup.findStatic(batchClass, placement.baseName(), MethodType.methodType(long.class,
                params.commandSourceStackType().resolveConstantDesc(lookup),
                params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).resolveConstantDesc(lookup))));
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
        if(state == State.READY_ON || state == State.EXECUTION_FAILED) setState(State.READY_OFF);
    }

    public void tryOn() {
        if(state == State.READY_OFF || state == State.EXECUTION_FAILED) setState(State.READY_ON);
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
            if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
            else diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, COMPILER_UNCAUGHT_EXCEPTION,
                "Uncaught exception caught in compilation thread", null, null, null, e));
            setState(State.FAILED);
            LOGGER.error("Compiler Thread threw an uncaught exception", e);
        });
        backgroundThread.start();
        return true;
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    /**
     * Computes a global hash that encodes the compiler version plus the complete
     * content of every function source.  If any source changes — or the compiler
     * is updated — the hash changes and the cache is invalidated.
     *
     * The hash also covers the set of compiled function IDs, because that set is
     * passed to generateFunctionClass and affects which call sites get a fast JVM
     * dispatch vs. vanilla fallback.
     */
    private String computeGlobalCacheHash(HashMap<DataLocation, List<String>> functionSources) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch(java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 is always available in Java
        }
        md.update(CACHE_VERSION.getBytes(StandardCharsets.UTF_8));
        md.update((byte) '\0');
        md.update(getCompilerVersion().getBytes(StandardCharsets.UTF_8));
        // Sort by function ID so the hash is order-independent
        var sortedEntries = new ArrayList<>(functionSources.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey(Comparator.comparing(DataLocation::toString)));
        for(var entry : sortedEntries) {
            md.update(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\0'); // key separator
            for(var line : entry.getValue()) {
                md.update(line.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            md.update((byte) '\0'); // entry separator
        }
        return HEX.formatHex(md.digest());
    }

    /** Relative cache path for batch class {@code idx}. */
    private static String batchCachePath(int idx) {
        return CACHE_BATCH_PREFIX + idx + ".class";
    }

    /**
     * Attempts to load all compiled batch classes from the on-disk cache.
     * Returns a populated result if the cache is valid, or {@code null} on any miss.
     *
     * Under the v2 batch layout, the manifest contains:
     *   - "hash"     — global compilation hash (must match)
     *   - "batches"  — list of relative class-file paths, one per batch
     *   - "functions" — { "&lt;ns&gt;:&lt;path&gt;": { "batch": N, "base": "fn_...", "hasMacros": bool }, ... }
     */
    @Nullable
    private CacheLoadResult tryLoadFromCache(String globalHash, Set<DataLocation> functionIds) throws Exception {
        var manifestPath = cacheDir.resolve(CACHE_MANIFEST);
        if(!Files.exists(manifestPath)) return null;

        var manifest = GSON.fromJson(Files.readString(manifestPath), JsonObject.class);
        if(manifest == null || !manifest.has("hash") || !manifest.has("batches") || !manifest.has("functions")) return null;
        if(!globalHash.equals(manifest.get("hash").getAsString())) return null;

        var dataClassPath = cacheDir.resolve(CACHE_DATA_CLASS);
        if(!Files.exists(dataClassPath)) return null;

        // Load batch class files
        var batchArr = manifest.getAsJsonArray("batches");
        var batchPaths = new ArrayList<Path>(batchArr.size());
        for(int i = 0; i < batchArr.size(); i++) {
            var p = cacheDir.resolve(batchArr.get(i).getAsString());
            if(!Files.exists(p)) return null;
            batchPaths.add(p);
        }

        var fnEntries = manifest.getAsJsonObject("functions");
        var placements = new HashMap<DataLocation, BatchClassPlanner.PlacedFunction>(functionIds.size());
        for(var loc : functionIds) {
            var key = loc.toString();
            if(!fnEntries.has(key)) return null;
            var obj = fnEntries.getAsJsonObject(key);
            int batch = obj.get("batch").getAsInt();
            if(batch < 0 || batch >= batchPaths.size()) return null;
            String base = obj.get("base").getAsString();
            boolean hasMacros = obj.has("hasMacros") && obj.get("hasMacros").getAsBoolean();
            placements.put(loc, new BatchClassPlanner.PlacedFunction(batch, base, hasMacros));
        }

        // Read bytes in parallel
        @SuppressWarnings("unchecked")
        byte[][] batchBytes = new byte[batchPaths.size()][];
        java.util.stream.IntStream.range(0, batchPaths.size()).parallel().forEach(i -> {
            try {
                batchBytes[i] = Files.readAllBytes(batchPaths.get(i));
            } catch(java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });

        return new CacheLoadResult(List.of(batchBytes), placements, Files.readAllBytes(dataClassPath));
    }

    private record CacheLoadResult(
        List<byte[]> batchBytes,
        Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
        byte[] dataClassBytes
    ) {}

    /**
     * Persists compilation results (batch classes + manifest + data class) to disk.
     * Failures are logged but do not abort the already-completed compilation.
     */
    private void saveToCache(String globalHash, List<byte[]> batchBytes,
                              Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
                              byte[] dataClassBytes) throws Exception {
        Files.createDirectories(cacheDir);
        Files.createDirectories(cacheDir.resolve(CACHE_BATCH_PREFIX));

        var manifest = new JsonObject();
        manifest.addProperty("hash", globalHash);

        var batchArr = new JsonArray();
        for(int i = 0; i < batchBytes.size(); i++) {
            var relPath = batchCachePath(i);
            Files.write(cacheDir.resolve(relPath), batchBytes.get(i));
            batchArr.add(relPath);
        }
        manifest.add("batches", batchArr);

        var functions = new JsonObject();
        for(var entry : placements.entrySet()) {
            var obj = new JsonObject();
            obj.addProperty("batch", entry.getValue().batchIndex());
            obj.addProperty("base", entry.getValue().baseName());
            obj.addProperty("hasMacros", entry.getValue().hasMacros());
            functions.add(entry.getKey().toString(), obj);
        }
        manifest.add("functions", functions);

        Files.write(cacheDir.resolve(CACHE_DATA_CLASS), dataClassBytes);
        Files.writeString(cacheDir.resolve(CACHE_MANIFEST), PRETTY_GSON.toJson(manifest));
    }

    // ── Load + handle registration ─────────────────────────────────────────────

    private boolean defineAndLoadFromBytes(List<byte[]> batchBytes,
                                           Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
                                           byte[] dataClassBytes,
                                           CompilationParams params, Object serverFunctionManager,
                                           int parsedFunctionCount) throws Throwable {
        // Define all batch classes — ~tens of defineClass calls even for thousands of functions
        var batchClasses = new ArrayList<Class<?>>(batchBytes.size());
        for(int i = 0; i < batchBytes.size(); i++) {
            var name = (BatchClassPlanner.BATCH_CLASS_PREFIX + i).replace('/', '.');
            batchClasses.add(defineClass(name, batchBytes.get(i)));
        }

        // Define and initialise the data class
        var desc    = DATA_CLASS.descriptorString();
        var defined = defineClass(desc.substring(1, desc.length() - 1).replace('/', '.'), dataClassBytes);
        var lk      = MethodHandles.lookup();
        defined.getDeclaredMethod("load",
            params.codeFieldStorage().minecraftServerType.resolveConstantDesc(lk),
            params.codeFieldStorage().serverFunctionManagerType.resolveConstantDesc(lk)
        ).invoke(null, minecraftServer, serverFunctionManager);
        resultField = lk.findStaticSetter(defined, RUN_RESULT_FIELD, long.class);

        // Register handles — findStatic by mangled method name within the batch class
        for(var entry : placements.entrySet()) {
            var key = entry.getKey();
            var placement = entry.getValue();
            try {
                registerFunction(batchClasses.get(placement.batchIndex()), key, placement, params);
            } catch(Throwable e) {
                if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_FAILED,
                    "Failed to create function handle (" + key + ")", null, key, null, e));
                LOGGER.error("Failed to create function handle ({}), clearing data", key, e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, List.of());
                FunctionCompilerReporter.sendReports();
                resetHandles();
                return false;
            }
        }
        return true;
    }

    // ── Main compilation entry point ───────────────────────────────────────────

    public void loadFunctionClasses(IArgumentLookup lookup, CompilationParams params,
                                    Object serverFunctionManager, boolean autoEnable) {
        if(ALLOW_EXTERNAL_DATA_ACCESS && GENERATE_REGISTRY_FILE) {
            ensureGeneratedDir();
            try {
                var registries    = DataRegistries.extractRegistries();
                var registriesStr = PRETTY_GSON_WITH_NULLS.toJson(registries);
                Files.writeString(generatedDir.resolve("registries.json"), registriesStr);
                LOGGER.info("Wrote {} lines into registries.json", registriesStr.lines().count());
            } catch(Exception e) {
                LOGGER.warn("Failed to write registries.json", e);
            }
        }

        // ── Parse ─────────────────────────────────────────────────────────────
        var startParse      = System.nanoTime();
        var functionSources = FunctionCodeGenerator.loadFunctionSources(sourceFiles);
        var definedFunctions = FunctionCodeGenerator.parseFunctions(functionSources, lookup, diagnostics);
        var parseTime       = (System.nanoTime() - startParse) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Parsed functions ({}->{}) in {}ms",
            sourceFiles.size(), definedFunctions.size(), (int)(parseTime * 1000));

        // ── Cache probe ───────────────────────────────────────────────────────
        // Compute hash BEFORE clearing functionSources.
        // The hash encodes: compiler version + all function IDs + all source lines.
        // Any change to sources or compiler automatically invalidates the cache.
        var globalHash = computeGlobalCacheHash(functionSources);
        functionSources.clear(); // no longer needed

        int parsedFunctionCount  = definedFunctions.size();
        int compiledCommandCount = definedFunctions.values().stream().mapToInt(List::size).sum();

        // Only use cache when debug dumps are disabled (cache stores production bytecode only)
        if(!DEBUG_DUMP_INSTRUCTIONS) {
            setState(State.LOADING);
            try {
                var cached = tryLoadFromCache(globalHash, definedFunctions.keySet());
                if(cached != null) {
                    LOGGER.info("Cache hit — loading {} functions across {} batch class(es) from disk (skipping compilation)",
                        cached.placements().size(), cached.batchBytes().size());
                    var startLoad = System.nanoTime();
                    if(!defineAndLoadFromBytes(cached.batchBytes(), cached.placements(), cached.dataClassBytes(), params, serverFunctionManager, parsedFunctionCount)) return;
                    LOGGER.info("Loaded compiled functions from cache in {}ms", (int)((System.nanoTime() - startLoad) / 1_000_000d));

                    var functionCountsData = new FunctionCountsData(sourceFiles.size(), parsedFunctionCount, handles.size(), handles.size());
                    var timingData         = new TimingData(parseTime, 0d, 0d, 0d, 0d);
                    var codeGenMetrics     = new CodeGenerationMetrics(compiledCommandCount, 0, 0, 0, 0, 0);
                    FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, codeGenMetrics, diagnostics.getDiagnostics());
                    FunctionCompilerReporter.sendReports();
                    finishLoading(autoEnable);
                    return;
                }
            } catch(Throwable e) {
                LOGGER.warn("Cache read failed ({}), falling back to full compilation", e.getMessage());
            }
        }

        // ── Compile + optimize + generate (cache miss path) ───────────────────
        //
        // One parallel pass: compile → optimize → generate class bytes per function.
        // Eliminates the two large intermediate IL maps that the old 3-phase pipeline
        // kept alive simultaneously (compiledFunctions + optimizedFunctions +
        // lowLevelFunctions all in memory at once).
        setState(State.COMPILING);
        LOGGER.info("Compiling functions");

        var startCompile      = System.nanoTime();
        var lowLevelFunctions = new ConcurrentHashMap<String, List<BytecodeInstruction>>();
        var totalFnFileSize   = new AtomicInteger(0);
        var totalDataFileSize = new AtomicInteger(0);
        var preOptCount       = new AtomicInteger(0);
        var postOptCount      = new AtomicInteger(0);
        var successCount      = new AtomicInteger(0);

        ConcurrentHashMap<DataLocation, List<Instruction>> debugCompiledMap  = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;
        ConcurrentHashMap<DataLocation, List<Instruction>> debugOptimizedMap = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;

        // Pass 1 (parallel): compile + optimize each function. A function that fails
        // here will NOT appear in compiledFunctionSet, so callers emit the vanilla
        // fallback path instead of invokestatic to a class that was never generated.
        LOGGER.info("[Phase 1] compile+optimize starting for {} function(s)", definedFunctions.size());
        var phase1Start = System.nanoTime();
        var phase1Progress = new AtomicInteger(0);
        int phase1Total = definedFunctions.size();
        int phase1Step = Math.max(100, phase1Total / 20);
        var optimizedFunctions = new ConcurrentHashMap<DataLocation, List<Instruction>>();
        // One virtual thread per function. We previously tried parallelStream
        // wrapped in commonPool().submit(...), but since JDK 10+ parallelStream
        // always dispatches to commonPool regardless of the enclosing pool, and
        // in this environment commonPool stubbornly refuses to scale past 1
        // active worker (observed via thread dump: 31/32 workers parked on
        // awaitWork while one worker chewed through everything at 99.5% CPU).
        // The virtual-thread scheduler uses its own dedicated FJP sized to
        // availableProcessors, so every carrier thread participates and the
        // usual CommonPool pathology is sidestepped. `ExecutorService.close()`
        // via try-with-resources waits for every submitted task.
        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for(var entry : definedFunctions.entrySet()) {
                final var key      = entry.getKey();
                final var commands = entry.getValue();
                executor.execute(() -> {
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
                    preOptCount.addAndGet(compiled.size());
                    if(DEBUG_DUMP_INSTRUCTIONS) debugCompiledMap.put(key, Instructions.addStaticRequirements(compiled));

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
                    postOptCount.addAndGet(optimized.size());
                    successCount.incrementAndGet();
                    if(DEBUG_DUMP_INSTRUCTIONS) debugOptimizedMap.put(key, optimized);

                    optimizedFunctions.put(key, optimized);

                    int done = phase1Progress.incrementAndGet();
                    if(done % phase1Step == 0 || done == phase1Total) {
                        LOGGER.info("[Phase 1] {}/{} ({}%) — latest: {}", done, phase1Total, (done * 100 / phase1Total), key);
                    }
                });
            }
        }
        LOGGER.info("[Phase 1] compile+optimize done in {}ms — {} succeeded", (System.nanoTime() - phase1Start) / 1_000_000, optimizedFunctions.size());

        var compiledFunctionSet = java.util.Set.copyOf(optimizedFunctions.keySet());

        // Identify which functions have macros (need a runInstanced method in their batch slot)
        var macroFunctions = ConcurrentHashMap.<DataLocation>newKeySet();
        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for(var e : optimizedFunctions.entrySet()) {
                final var key   = e.getKey();
                final var value = e.getValue();
                executor.execute(() -> {
                    for(var instr : value) {
                        if(instr.type() == InstructionTypes.LOAD_MACRO) {
                            macroFunctions.add(key);
                            return;
                        }
                    }
                });
            }
        }

        // Pass 1.5: plan batch assignments. Deterministic — required for cache stability.
        LOGGER.info("[Phase 1.5] planning batches for {} function(s) ({} with macros)", optimizedFunctions.size(), macroFunctions.size());
        var phase15Start = System.nanoTime();
        var planner = BatchClassPlanner.plan(optimizedFunctions, macroFunctions);
        LOGGER.info("[Phase 1.5] planned {} function(s) into {} batch class(es) in {}ms",
            optimizedFunctions.size(), planner.batchClasses().size(), (System.nanoTime() - phase15Start) / 1_000_000);

        // Group function IDs by planned batch (deterministic order for cache stability)
        var batchMembers = new ArrayList<ArrayList<DataLocation>>(planner.batchClasses().size());
        for(int i = 0; i < planner.batchClasses().size(); i++) batchMembers.add(new ArrayList<>());
        for(var loc : optimizedFunctions.keySet()) batchMembers.get(planner.placement(loc).batchIndex()).add(loc);
        for(var list : batchMembers) list.sort((a, b) -> a.toString().compareTo(b.toString()));

        // Pass 2 + 2.5 fused: per-batch parallel, per-function sequential within batch.
        // Each batch-worker emits its functions one at a time directly into its own
        // ClassBuilder and drops the intermediate IR before moving to the next function.
        // This bounds heap to O(largest-function-IR × parallelism) rather than
        // O(all-functions-IR), which previously caused GC thrashing on big datapacks.
        int batchCount = planner.batchClasses().size();
        byte[][] batchBytesArr = new byte[batchCount][];
        var successfulPlacements = new ConcurrentHashMap<DataLocation, BatchClassPlanner.PlacedFunction>();

        LOGGER.info("[Phase 2] emit+assemble starting — {} batch(es), parallelism={}",
            batchCount, java.util.concurrent.ForkJoinPool.commonPool().getParallelism());
        var phase2Start = System.nanoTime();
        var batchesDone = new AtomicInteger(0);

        try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        for(int bi0 = 0; bi0 < batchCount; bi0++) {
            final int bi = bi0;
            executor.execute(() -> {
            var batchStart = System.nanoTime();
            var batchClassDesc = planner.batchClasses().get(bi);
            var members = batchMembers.get(bi);
            byte[] bytes = ClassGenerator.createClassGenerator(batchClassDesc, gen -> {
                for(var key : members) {
                    var optimized = optimizedFunctions.get(key);
                    if(optimized == null) continue;
                    var placement = planner.placement(key);
                    try {
                        var fer = FunctionCodeGenerator.emitFunctionMethods(
                            params, key, optimized, compiledFunctionSet, planner, placement,
                            batchClassDesc, lowLevelFunctions);
                        for(var method : fer.methods()) {
                            gen.addFunction(method.name(), method.instructions(), method.type());
                        }
                        successfulPlacements.put(key, placement);
                    } catch(DiagnosticException e) {
                        diagnostics.addDiagnosticSafe(e.toDiagnostic());
                        LOGGER.error("Failed to emit methods for function ({}), skipping", key, e);
                    } catch(Throwable e) {
                        diagnostics.addDiagnosticSafe(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_CLASS_LOAD_FAILED,
                            "Failed to load function (" + key + ")", null, key, null, e));
                        LOGGER.error("Failed to load function ({}), skipping", key, e);
                    }
                }
            });
            batchBytesArr[bi] = bytes;
            totalFnFileSize.addAndGet(bytes.length);
            long batchMs = (System.nanoTime() - batchStart) / 1_000_000;
            int done = batchesDone.incrementAndGet();
            LOGGER.info("[Phase 2] batch {}/{} done: {} function(s), {} KB, {}ms",
                done, batchCount, members.size(), bytes.length / 1024, batchMs);
            });
        }
        }
        LOGGER.info("[Phase 2] emit+assemble done in {}ms", (System.nanoTime() - phase2Start) / 1_000_000);
        optimizedFunctions.clear();
        var batchBytes = new ArrayList<byte[]>(batchCount);
        for(var b : batchBytesArr) batchBytes.add(b);

        definedFunctions.clear();
        var compileTime = (System.nanoTime() - startCompile) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Compiled+optimized+emitted {} functions across {} batch class(es) in {}ms",
            successfulPlacements.size(), batchBytes.size(), (int)(compileTime * 1000));

        // Debug HL dump (dead code in production)
        if(DEBUG_DUMP_INSTRUCTIONS) {
            for(boolean postOpt : new boolean[]{true, false}) {
                var map    = postOpt ? debugOptimizedMap : debugCompiledMap;
                var suffix = postOpt ? "high_level" : "high_level_no_opt";
                var dump   = new StringBuilder();
                var json   = new JsonObject();
                var entries = new ArrayList<>(map.entrySet());
                entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
                for(var e : entries) {
                    var instrList = e.getValue();
                    dump.append("┌──\n│ function ").append(e.getKey()).append("\n│ ┗━ ").append(instrList.size()).append(" instruction(s)\n│\n");
                    Instruction.printInstructions(instrList, line -> dump.append("│ ┗━ ").append(line).append("\n"));
                    dump.append("└──\n\n");
                    json.add(e.getKey().toString(), Instruction.generateInstructionsJson(instrList));
                }
                ensureGeneratedDir();
                try {
                    String txt = dump.toString();
                    Files.writeString(generatedDir.resolve("instruction_dump_" + suffix + ".txt"), txt);
                    LOGGER.info("Wrote {} lines into instruction_dump_{}.txt", txt.lines().count(), suffix);
                    String jsonStr = PRETTY_GSON_NO_HTML_ESCAPE.toJson(json);
                    Files.writeString(generatedDir.resolve("instruction_dump_" + suffix + ".json"), jsonStr);
                    LOGGER.info("Wrote {} lines into instruction_dump_{}.json", jsonStr.lines().count(), suffix);
                } catch(Exception e) {
                    LOGGER.warn("Failed to write instruction_dump_{} files", suffix, e);
                }
            }
        }

        // ── Load: generate data class bytes, then defineClass everything ──────
        LOGGER.info("Loading functions");
        setState(State.LOADING);
        var startLoader = System.nanoTime();

        // Generate data class bytes *before* defineClass so we can persist to cache
        byte[] dataClassBytes;
        try {
            dataClassBytes = params.codeFieldStorage().generate(new HashMap<>(lowLevelFunctions));
            totalDataFileSize.addAndGet(dataClassBytes.length);
        } catch(Throwable e) {
            if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
            LOGGER.error("Failed to generate data class", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("generate_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }

        // Persist to cache before defineClass (while we still have all bytes)
        if(!DEBUG_DUMP_INSTRUCTIONS) {
            try {
                saveToCache(globalHash, batchBytes, successfulPlacements, dataClassBytes);
                LOGGER.info("Saved {} compiled functions across {} batch class(es) to cache",
                    successfulPlacements.size(), batchBytes.size());
            } catch(Exception e) {
                LOGGER.warn("Failed to save compilation cache ({}), compilation will continue", e.getMessage());
            }
        }

        // Sequential defineClass pass — one defineClass per BATCH, not per function.
        // On a 5k-function datapack this is ~tens of defineClass calls vs. thousands,
        // which is the whole point of batching.
        var batchClasses = new ArrayList<Class<?>>(batchBytes.size());
        for(int i = 0; i < batchBytes.size(); i++) {
            var name = (BatchClassPlanner.BATCH_CLASS_PREFIX + i).replace('/', '.');
            batchClasses.add(defineClass(name, batchBytes.get(i)));
        }
        // Keep raw batch bytes around so we can dump .class for any function whose
        // findStatic fails later (codegen bug in some method of that batch).
        var rawBatchBytes = new ArrayList<>(batchBytes);
        batchBytes.clear();

        // Define and initialise the data class
        try {
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
            LOGGER.error("Failed to load data class", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }

        var classLoadTime = (System.nanoTime() - startLoader) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded classes in {}ms", (int)(classLoadTime * 1000));

        // Debug LL dump (dead code in production)
        if(DEBUG_DUMP_INSTRUCTIONS) {
            var dump   = new StringBuilder();
            var json   = new JsonObject();
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
            ensureGeneratedDir();
            try {
                String txt = dump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.txt"), txt);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.txt", txt.lines().count());
                String jsonStr = PRETTY_GSON_NO_HTML_ESCAPE.toJson(json);
                Files.writeString(generatedDir.resolve("instruction_dump_low_level.json"), jsonStr);
                LOGGER.info("Wrote {} lines into instruction_dump_low_level.json", jsonStr.lines().count());
            } catch(Exception e) {
                LOGGER.warn("Failed to write instruction_dump_low_level files", e);
            }
        }

        if(DEBUG_GENERATE_GRAPH) {
            ensureGeneratedDir();
            if(DEBUG_DUMP_INSTRUCTIONS) GraphGenerator.generateGraphs(new HashMap<>(debugOptimizedMap), generatedDir);
            BytecodeGraphGenerator.generateBytecodeGraphs(lowLevelFunctions, generatedDir);
        }

        // ── Handle registration ───────────────────────────────────────────────
        // Per-function findStatic is lazy in the JVM, so a single broken method
        // in a batch class still only kills its own handle — callers fall back to
        // vanilla for that one function. The whole batch survives.
        var startHandles = System.nanoTime();
        int failedCount = 0;
        Path failedDumpDir = null;
        for(var entry : successfulPlacements.entrySet()) {
            var key = entry.getKey();
            var placement = entry.getValue();
            try {
                registerFunction(batchClasses.get(placement.batchIndex()), key, placement, params);
            } catch(Throwable e) {
                if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
                diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_FAILED,
                    "Failed to create function handle (" + key + "), falling back to vanilla", null, key, null, e));
                LOGGER.warn("Failed to create function handle ({}), falling back to vanilla for this function", key, e);
                failedCount++;

                // Dump IL + raw batch .class for offline diagnosis.
                try {
                    if(failedDumpDir == null) {
                        failedDumpDir = generatedDir.resolve("failed_functions");
                        Files.createDirectories(failedDumpDir);
                    }
                    var safeName = (key.namespace() + "__" + key.path()).replace('/', '_');
                    // lowLevelFunctions is keyed by displayPrefix (the mangled base name),
                    // e.g. "fn_vanilla_death_soul_abc12345#fn_...". Match all methods whose
                    // prefix equals this function's mangled base.
                    var classPrefix = placement.baseName() + "#";

                    var ilDump = new StringBuilder();
                    ilDump.append("# ").append(key).append("\n");
                    ilDump.append("# batch=").append(placement.batchIndex())
                          .append(" method=").append(placement.baseName()).append("\n");
                    ilDump.append("# exception: ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n\n");
                    var methods = lowLevelFunctions.entrySet().stream()
                        .filter(en -> en.getKey().startsWith(classPrefix))
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
                    for(var en : methods) {
                        ilDump.append("┌── method ").append(en.getKey()).append(" (")
                            .append(en.getValue().size()).append(" instructions)\n");
                        for(var instr : en.getValue()) {
                            ilDump.append("│ ").append(BytecodeFormatter.formatInstruction(instr)).append("\n");
                        }
                        ilDump.append("└──\n\n");
                    }
                    Files.writeString(failedDumpDir.resolve(safeName + ".il.txt"), ilDump.toString());

                    var bBytes = rawBatchBytes.get(placement.batchIndex());
                    if(bBytes != null) {
                        Files.write(failedDumpDir.resolve(safeName + ".batch" + placement.batchIndex() + ".class"), bBytes);
                    }
                    LOGGER.warn("  → dumped IL and batch .class to {}/{}.*", failedDumpDir, safeName);
                } catch(Exception dumpEx) {
                    LOGGER.warn("  → failed to dump diagnostic info for {}", key, dumpEx);
                }
            }
        }
        rawBatchBytes.clear();
        if(failedCount > 0) {
            LOGGER.warn("{} function(s) fell back to vanilla due to codegen issues", failedCount);
        }
        var handleLoadTime = (System.nanoTime() - startHandles) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded handles in {}ms", (int)(handleLoadTime * 1000));

        // ── Metrics ───────────────────────────────────────────────────────────
        var functionCountsData = new FunctionCountsData(sourceFiles.size(), parsedFunctionCount, successCount.get(), successfulPlacements.size());
        var timingData         = new TimingData(parseTime, compileTime, 0d, classLoadTime, handleLoadTime);
        var codeGenMetrics     = new CodeGenerationMetrics(
            compiledCommandCount, preOptCount.get(), postOptCount.get(),
            lowLevelFunctions.values().stream().mapToInt(List::size).sum(),
            totalFnFileSize.get(), totalDataFileSize.get());
        FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, codeGenMetrics, diagnostics.getDiagnostics());
        FunctionCompilerReporter.sendReports();

        finishLoading(autoEnable);
    }

    private void finishLoading(boolean autoEnable) {
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
            ensureGeneratedDir();
            try {
                Files.write(generatedDir.resolve(name + ".class"), data);
            } catch(Exception e) {
                LOGGER.warn("Failed to dump class file {}", name, e);
            }
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
            diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, FUNCTION_EXECUTION_FAILED,
                "Failed to run function (" + location + ")", null, new DataLocation(location.toString()), null, e));
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
