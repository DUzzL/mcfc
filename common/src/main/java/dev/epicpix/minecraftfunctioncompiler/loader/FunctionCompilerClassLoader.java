package dev.epicpix.minecraftfunctioncompiler.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.GlobalConfig;
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
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
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
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMPILER_UNCAUGHT_EXCEPTION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_EXECUTION_FAILED;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_LOAD_FAILED;

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
    private static final String CACHE_VERSION      = "v2.9";
    private static final String CACHE_MANIFEST     = "manifest.json";
    private static final String CACHE_DATA_PREFIX  = "data/";
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

    /** Dispatch slots by function id. Values are never removed; a failed function has {@code function == null}. */
    private final ConcurrentHashMap<TIdentifier, CompiledFunction.Entry> handles = new ConcurrentHashMap<>();

    public final ClassLoader parent;
    public final TMinecraftServer minecraftServer;
    private final Path generatedDir;
    private final Path cacheDir;
    /** Number of functions vanilla loaded, fixed at construction (sources themselves are read lazily and dropped after parsing). */
    private final int totalFunctionCount;
    private final DiagnosticCollector diagnostics = new DiagnosticCollector();

    private volatile State state = State.NOTHING_LOADED;

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

    public FunctionCompilerClassLoader(ClassLoader parent, Path generatedDir, TMinecraftServer minecraftServer, int totalFunctionCount) {
        super(parent);
        this.parent = parent;
        this.generatedDir = generatedDir;
        this.cacheDir = generatedDir.resolve("cache");
        this.minecraftServer = minecraftServer;
        this.totalFunctionCount = totalFunctionCount;
    }

    /**
     * Reads the source lines of every function vanilla loaded. Called once per compile, on the compile
     * thread; the result is dropped as soon as it has been hashed (and parsed on a cache miss).
     */
    protected abstract Map<DataLocation, List<String>> readFunctionSources();

    /** Runs {@code task} on the server thread (inline when already on it). */
    protected abstract void runOnServerThread(Runnable task);

    /**
     * Returns a version string that identifies the current compiler build.
     * When this string changes (e.g. after a mod update), the entire cache is
     * invalidated automatically.  Override in platform-specific subclasses.
     */
    protected String getCompilerVersion() {
        return "";
    }

    protected abstract TIdentifier toIdentifier(DataLocation key);

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

    /** Functions that are compiled and have not been disabled by a runtime failure. */
    public int loadedFunctionCount() {
        int count = 0;
        for(var entry : handles.values()) if(entry.function != null) count++;
        return count;
    }

    public int totalFunctionCount() {
        return totalFunctionCount;
    }

    // ── Compile coordination ───────────────────────────────────────────────────
    //
    // Exactly one compile runs at a time, on one daemon thread. Starting a compile cancels the previous
    // one; the cancelled compile notices at its next pool task / phase boundary and unwinds before the
    // new one starts, so compiles never overlap on the static DataRegistries, the shared pool or the cache
    // dir. Queued compiles that were superseded before they started return immediately, so a burst of
    // /reloads collapses into the last one. Results (state, onComplete) are published on the server thread
    // and only if the compile is still the newest one.

    private static final Object COORDINATOR_LOCK = new Object();
    private static @Nullable FunctionCompilerClassLoader<?, ?, ?, ?> newestCompile;
    private static final java.util.concurrent.ExecutorService COMPILE_THREAD = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(null, () -> {
            LowPriorityCompileExecutor.applyCompileNiceToCurrentThread();
            r.run();
        }, "Function Compiler", LowPriorityCompileExecutor.COMPILE_STACK_SIZE);
        thread.setDaemon(true);
        thread.setPriority(LowPriorityCompileExecutor.COMPILE_THREAD_PRIORITY);
        return thread;
    });

    /** Thrown by {@link #checkCancelled()} to unwind a superseded compile. */
    public static final class CompileCancelledException extends RuntimeException {
        private CompileCancelledException() {
            super("Function compilation superseded", null, false, false);
        }
    }

    private volatile boolean cancelled;
    private volatile @Nullable State readyState;

    /** Cheap check for pool tasks: true once a newer compile started or the server is stopping. */
    protected final boolean isCancelled() {
        return cancelled;
    }

    protected final void checkCancelled() {
        if(cancelled) throw new CompileCancelledException();
    }

    private boolean isNewest() {
        synchronized(COORDINATOR_LOCK) {
            return !cancelled && newestCompile == this;
        }
    }

    /** Cancels any running compile and drops the reference to it (and thereby to its server). */
    public static void cancelAllCompiles() {
        synchronized(COORDINATOR_LOCK) {
            if(newestCompile != null) newestCompile.cancelled = true;
            newestCompile = null;
        }
    }

    /**
     * Makes this loader the newest compile and runs {@code compile} after any older compile has unwound.
     * With {@code blocking} the calling (server) thread waits for the compile; otherwise it returns at once.
     * {@code onComplete} runs on the server thread on every exit path once this compile is finished or superseded.
     */
    protected final void startCompile(Runnable compile, boolean blocking, Runnable onComplete) {
        synchronized(COORDINATOR_LOCK) {
            if(newestCompile != null) newestCompile.cancelled = true;
            newestCompile = this;
        }
        setState(State.PARSING);
        Runnable job = () -> {
            try {
                checkCancelled();
                compile.run();
            } catch(CompileCancelledException e) {
                LOGGER.info("Abandoned superseded function compilation");
            } catch(Throwable e) {
                if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
                else diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, COMPILER_UNCAUGHT_EXCEPTION,
                    "Uncaught exception caught in compilation thread", null, null, null, e));
                LOGGER.error("Function compilation failed", e);
                readyState = State.FAILED;
                if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) {
                    if(!blocking) runOnServerThread(() -> publish(onComplete));
                    throw vme;
                }
            }
            if(!blocking) runOnServerThread(() -> publish(onComplete));
        };
        if(!blocking) {
            COMPILE_THREAD.execute(job);
            return;
        }
        try {
            COMPILE_THREAD.submit(job).get();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelled = true;
        } catch(java.util.concurrent.ExecutionException e) {
            LOGGER.error("Function compilation failed", e.getCause());
        }
        publish(onComplete);
    }

    /** Server thread: expose the outcome of the compile if it is still the newest one. */
    private void publish(Runnable onComplete) {
        if(isNewest()) {
            setState(readyState != null ? readyState : State.FAILED);
        }
        onComplete.run();
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────
    //
    // Layout: cache/<hash>/{manifest.json, data.class, batch/<n>.class}. A compile writes into a private
    // temp dir and atomically renames it into place, so readers never see a half-written entry and
    // concurrent servers sharing a game dir can't interleave files. The newest CACHE_KEEP entries are kept
    // (LRU by directory mtime, touched on every hit) so switching between a few datapack states stays warm.

    private static final int CACHE_KEEP = 4;

    /**
     * Hash of every input that influences generated code: cache format, compiler build (mod version, commit,
     * MC version), the raw lines of every function vanilla loaded (which also fixes the set of loaded
     * functions), and every registry snapshot the compiler consults (function tags, advancements, predicates,
     * entity types + tags, attributes, mob effects, scoreboard criteria). Config flags don't affect codegen.
     */
    private String computeGlobalCacheHash(Map<DataLocation, List<String>> functionSources) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch(java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 is always available in Java
        }
        digestString(md, CACHE_VERSION);
        digestString(md, getCompilerVersion());
        digestString(md, dev.epicpix.minecraftfunctioncompiler.CompileConstants.versionCommit);
        var sortedEntries = new ArrayList<>(functionSources.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey(Comparator.comparing(DataLocation::toString)));
        for(var entry : sortedEntries) {
            digestString(md, entry.getKey().toString());
            for(var line : entry.getValue()) {
                md.update(line.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            md.update((byte) '\0');
        }
        digestRegistry(md, "advancement", DataRegistries.ADVANCEMENT.elementEntrySet());
        digestRegistry(md, "advancement_tags", DataRegistries.ADVANCEMENT.tagsEntrySet());
        digestRegistry(md, "attribute", DataRegistries.ATTRIBUTE.elementEntrySet());
        digestRegistry(md, "attribute_tags", DataRegistries.ATTRIBUTE.tagsEntrySet());
        digestRegistry(md, "entity_type", DataRegistries.ENTITY_TYPE.elementEntrySet());
        digestRegistry(md, "entity_type_tags", DataRegistries.ENTITY_TYPE.tagsEntrySet());
        digestRegistry(md, "function_tags", DataRegistries.FUNCTION_TAGS.entrySet());
        digestRegistry(md, "mob_effect", DataRegistries.MOB_EFFECT.elementEntrySet());
        digestRegistry(md, "mob_effect_tags", DataRegistries.MOB_EFFECT.tagsEntrySet());
        digestRegistry(md, "predicate", DataRegistries.PREDICATE.elementEntrySet());
        digestRegistry(md, "predicate_tags", DataRegistries.PREDICATE.tagsEntrySet());
        digestRegistry(md, "scoreboard_criteria", DataRegistries.SCOREBOARD_CRITERIA.entrySet());
        return HEX.formatHex(md.digest());
    }

    private static void digestString(MessageDigest md, String value) {
        md.update(value.getBytes(StandardCharsets.UTF_8));
        md.update((byte) '\0');
    }

    /** Order-independent digest of a registry: entries sorted by key, values by their (record) toString. */
    private static void digestRegistry(MessageDigest md, String name, Set<? extends Map.Entry<?, ?>> entries) {
        digestString(md, name);
        var lines = new ArrayList<String>(entries.size());
        for(var entry : entries) lines.add(entry.getKey() + "=" + canonical(entry.getValue()));
        lines.sort(null);
        for(var line : lines) digestString(md, line);
        md.update((byte) '\1');
    }

    /**
     * Order-insensitive rendering of a registry value. Advancement criteria come from an unordered map whose
     * iteration order differs between runs; the compiler only tests membership, so they are hashed sorted.
     */
    private static String canonical(Object value) {
        if(value instanceof dev.epicpix.minecraftfunctioncompiler.data.AdvancementData adv) {
            var criteria = new ArrayList<>(adv.criteria());
            criteria.sort(null);
            return adv.location() + "|" + adv.parent() + "|" + criteria;
        }
        return String.valueOf(value);
    }

    private static String sha256(byte[] data) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch(java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** Relative cache path for batch class {@code idx}. */
    private static String batchCachePath(int idx) {
        return CACHE_BATCH_PREFIX + idx + ".class";
    }

    private static void deleteRecursively(Path path) {
        if(!Files.exists(path)) return;
        try(var walk = Files.walk(path)) {
            for(var p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch(Exception e) {
            LOGGER.warn("Failed to delete {}", path, e);
        }
    }

    /**
     * Loads the cache entry for {@code globalHash}, or returns {@code null} on a miss. Every file is checked
     * against the SHA-256 recorded in the manifest; a corrupt entry is deleted.
     *
     * Manifest: "hash", "batches" (relative class paths), "sha256" ({path: hex}), "fallback" (ids that run via
     * vanilla), "functions" ({id: {batch, base, hasMacros}}).
     */
    @Nullable
    private CacheLoadResult tryLoadFromCache(String globalHash, Set<DataLocation> functionIds) throws Exception {
        var entryDir = cacheDir.resolve(globalHash);
        var manifestPath = entryDir.resolve(CACHE_MANIFEST);
        if(!Files.exists(manifestPath)) {
            LOGGER.info("Cache miss: no entry for {}", globalHash);
            return null;
        }
        var result = readCacheEntry(entryDir, globalHash, functionIds);
        if(result == null) {
            deleteRecursively(entryDir);
        } else {
            try {
                Files.setLastModifiedTime(entryDir, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            } catch(Exception ignored) {
                // LRU order is best effort
            }
        }
        return result;
    }

    @Nullable
    private CacheLoadResult readCacheEntry(Path entryDir, String globalHash, Set<DataLocation> functionIds) throws Exception {
        var manifest = GSON.fromJson(Files.readString(entryDir.resolve(CACHE_MANIFEST)), JsonObject.class);
        if(manifest == null || !manifest.has("hash") || !manifest.has("batches") || !manifest.has("functions") || !manifest.has("sha256") || !manifest.has("fallback") || !manifest.has("data")) {
            LOGGER.warn("Cache entry {} has an invalid manifest, discarding it", entryDir);
            return null;
        }
        if(!globalHash.equals(manifest.get("hash").getAsString())) {
            LOGGER.warn("Cache entry {} has a mismatching hash, discarding it", entryDir);
            return null;
        }
        var checksums = manifest.getAsJsonObject("sha256");

        var batchArr = manifest.getAsJsonArray("batches");
        var batchPaths = new ArrayList<String>(batchArr.size());
        for(var el : batchArr) batchPaths.add(el.getAsString());

        var fallbackSet = new HashSet<String>();
        for(var el : manifest.getAsJsonArray("fallback")) fallbackSet.add(el.getAsString());

        var fnEntries = manifest.getAsJsonObject("functions");
        var placements = new HashMap<DataLocation, BatchClassPlanner.PlacedFunction>(functionIds.size());
        for(var loc : functionIds) {
            var key = loc.toString();
            if(!fnEntries.has(key)) {
                if(fallbackSet.contains(key)) continue; // known vanilla-fallback, no batch class expected
                LOGGER.warn("Cache entry {} does not cover function '{}', discarding it", entryDir, key);
                return null;
            }
            var obj = fnEntries.getAsJsonObject(key);
            int batch = obj.get("batch").getAsInt();
            if(batch < 0 || batch >= batchPaths.size()) {
                LOGGER.warn("Cache entry {}: function '{}' has invalid batch index {}, discarding it", entryDir, key, batch);
                return null;
            }
            placements.put(loc, new BatchClassPlanner.PlacedFunction(batch, obj.get("base").getAsString(), obj.get("hasMacros").getAsBoolean()));
        }

        byte[][] batchBytes = new byte[batchPaths.size()][];
        for(int i = 0; i < batchPaths.size(); i++) {
            batchBytes[i] = readVerified(entryDir, batchPaths.get(i), checksums);
            if(batchBytes[i] == null) return null;
        }
        var dataClasses = new ArrayList<CodeFieldStorage.DataClass>();
        for(var el : manifest.getAsJsonArray("data")) {
            var obj = el.getAsJsonObject();
            var bytes = readVerified(entryDir, obj.get("path").getAsString(), checksums);
            if(bytes == null) return null;
            dataClasses.add(new CodeFieldStorage.DataClass(ClassDesc.ofInternalName(obj.get("class").getAsString()), bytes));
        }
        return new CacheLoadResult(List.of(batchBytes), placements, dataClasses);
    }

    private static byte @Nullable [] readVerified(Path entryDir, String relPath, JsonObject checksums) throws java.io.IOException {
        var path = entryDir.resolve(relPath);
        if(!Files.exists(path) || !checksums.has(relPath)) {
            LOGGER.warn("Cache entry {}: {} missing, discarding it", entryDir, relPath);
            return null;
        }
        var bytes = Files.readAllBytes(path);
        if(!sha256(bytes).equals(checksums.get(relPath).getAsString())) {
            LOGGER.warn("Cache entry {}: {} is corrupt, discarding it", entryDir, relPath);
            return null;
        }
        return bytes;
    }

    private record CacheLoadResult(
        List<byte[]> batchBytes,
        Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
        List<CodeFieldStorage.DataClass> dataClasses
    ) {}

    /**
     * Persists compilation results (batch classes + manifest + data class) to cache/&lt;hash&gt;/ via a temp dir
     * and an atomic rename. Skipped when this compile has been superseded.
     */
    private void saveToCache(String globalHash, List<byte[]> batchBytes,
                              Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
                              Set<DataLocation> fallbackFunctions,
                              List<CodeFieldStorage.DataClass> dataClasses) throws Exception {
        checkCancelled();
        Files.createDirectories(cacheDir);
        var entryDir = cacheDir.resolve(globalHash);
        var tmpDir = Files.createTempDirectory(cacheDir, globalHash + ".tmp");
        try {
            Files.createDirectories(tmpDir.resolve(CACHE_BATCH_PREFIX));
            Files.createDirectories(tmpDir.resolve(CACHE_DATA_PREFIX));
            var manifest = new JsonObject();
            manifest.addProperty("hash", globalHash);
            var checksums = new JsonObject();

            var batchArr = new JsonArray();
            for(int i = 0; i < batchBytes.size(); i++) {
                var relPath = batchCachePath(i);
                Files.write(tmpDir.resolve(relPath), batchBytes.get(i));
                checksums.addProperty(relPath, sha256(batchBytes.get(i)));
                batchArr.add(relPath);
            }
            manifest.add("batches", batchArr);

            // Functions without a compiled method (parse/compile/emit failure, unsupported shapes): they run via
            // vanilla. Listed so a cache load can tell "known fallback" from "function added since".
            var fallbackArr = new JsonArray();
            var sortedFallback = new ArrayList<>(fallbackFunctions);
            sortedFallback.sort(Comparator.comparing(DataLocation::toString));
            for(var loc : sortedFallback) fallbackArr.add(loc.toString());
            manifest.add("fallback", fallbackArr);

            var functions = new JsonObject();
            for(var entry : placements.entrySet()) {
                var obj = new JsonObject();
                obj.addProperty("batch", entry.getValue().batchIndex());
                obj.addProperty("base", entry.getValue().baseName());
                obj.addProperty("hasMacros", entry.getValue().hasMacros());
                functions.add(entry.getKey().toString(), obj);
            }
            manifest.add("functions", functions);

            var dataArr = new JsonArray();
            for(int i = 0; i < dataClasses.size(); i++) {
                var dataClass = dataClasses.get(i);
                var relPath = CACHE_DATA_PREFIX + i + ".class";
                Files.write(tmpDir.resolve(relPath), dataClass.bytes());
                checksums.addProperty(relPath, sha256(dataClass.bytes()));
                var obj = new JsonObject();
                obj.addProperty("class", dataClass.binaryName().replace('.', '/'));
                obj.addProperty("path", relPath);
                dataArr.add(obj);
            }
            manifest.add("data", dataArr);
            manifest.add("sha256", checksums);
            Files.writeString(tmpDir.resolve(CACHE_MANIFEST), PRETTY_GSON.toJson(manifest));

            checkCancelled();
            deleteRecursively(entryDir);
            try {
                Files.move(tmpDir, entryDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch(java.nio.file.FileAlreadyExistsException | java.nio.file.DirectoryNotEmptyException e) {
                // Another process published the same hash first; its content is equivalent.
            }
        } finally {
            deleteRecursively(tmpDir);
        }
        pruneCache();
    }

    /** Keeps the CACHE_KEEP most recently used entries and removes leftovers of the old flat layout. */
    private void pruneCache() {
        try(var list = Files.list(cacheDir)) {
            var entries = new ArrayList<Path>();
            for(var p : list.toList()) {
                var name = p.getFileName().toString();
                if(Files.isDirectory(p) && name.length() == 64 && !name.contains(".")) entries.add(p);
                else if(!name.contains(".tmp")) deleteRecursively(p); // legacy manifest.json/data.class/batch/
                else if(Files.getLastModifiedTime(p).toMillis() < System.currentTimeMillis() - 3_600_000L) deleteRecursively(p); // crashed writer
            }
            entries.sort(Comparator.comparing((Path p) -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch(java.io.IOException e) {
                    return 0L;
                }
            }).reversed());
            for(int i = CACHE_KEEP; i < entries.size(); i++) deleteRecursively(entries.get(i));
        } catch(Exception e) {
            LOGGER.warn("Failed to prune compilation cache", e);
        }
    }

    // ── Load + handle registration ─────────────────────────────────────────────

    /** Cache hit: defines the cached classes and registers their functions. Throws if a cached class
     *  doesn't define or link, so the caller can fall back to a full compile (in a fresh loader). */
    private boolean defineAndLoadFromBytes(List<byte[]> batchBytes,
                                           Map<DataLocation, BatchClassPlanner.PlacedFunction> placements,
                                           List<CodeFieldStorage.DataClass> dataClasses,
                                           CompilationParams params, Object serverFunctionManager,
                                           int parsedFunctionCount) throws Throwable {
        var defined = defineGenerated(new ArrayList<>(batchBytes), () -> dataClasses, null);
        initDataClass(defined, params, serverFunctionManager);
        registerAll(defined, placements, params);
        return true;
    }

    /** Generated classes of one successful load attempt. */
    private record DefinedClasses(BatchCompiler.GeneratedClassLoader loader, List<Class<?>> batches, Class<?> dataClass, List<CodeFieldStorage.DataClass> dataClasses) {}

    private static final int MAX_LINK_ATTEMPTS = 4;

    /**
     * Defines and links every batch, then defines the data class, all in a fresh
     * {@link BatchCompiler.GeneratedClassLoader}: nothing is ever defined in this loader, so a failed
     * attempt (bad cache entry, batch HotSpot rejects) can be retried under the same class names.
     * A batch that fails to link is replaced by {@code repair(index, error)} and the whole set is
     * redefined in a new loader; without {@code repair} the link error is thrown. The data class bytes
     * are produced after the batches are final, since repair stubs add data-class constants.
     */
    private DefinedClasses defineGenerated(List<byte[]> batchBytes, java.util.function.Supplier<List<CodeFieldStorage.DataClass>> dataClasses,
                                           java.util.function.@Nullable BiFunction<Integer, Throwable, byte[]> repair) {
        for(int attempt = 1; ; attempt++) {
            checkCancelled();
            var loader = new BatchCompiler.GeneratedClassLoader(this);
            var classes = new ArrayList<Class<?>>(batchBytes.size());
            int failed = -1;
            Throwable failure = null;
            for(int i = 0; i < batchBytes.size(); i++) {
                var name = BatchCompiler.binaryName(i);
                dumpClassFile(name, batchBytes.get(i));
                try {
                    classes.add(BatchCompiler.GeneratedClassLoader.link(loader, name, batchBytes.get(i)));
                } catch(LinkageError e) {
                    failed = i;
                    failure = e;
                    break;
                }
            }
            if(failed < 0) {
                var generated = dataClasses.get();
                Class<?> dataClass = null;
                for(var data : generated) {
                    dumpClassFile(data.binaryName(), data.bytes());
                    var defined = loader.define(data.binaryName(), data.bytes());
                    if(data.type().equals(DATA_CLASS)) dataClass = defined;
                }
                if(dataClass == null) throw new IllegalStateException("Data classes lack " + DATA_CLASS.displayName());
                return new DefinedClasses(loader, classes, dataClass, generated);
            }
            if(repair == null || attempt >= MAX_LINK_ATTEMPTS) {
                throw new IllegalStateException("Generated class " + BatchCompiler.binaryName(failed) + " failed to link", failure);
            }
            LOGGER.error("HotSpot rejected verified batch {}; isolating the failing functions", failed, failure);
            batchBytes.set(failed, repair.apply(failed, failure));
        }
    }

    private void initDataClass(DefinedClasses defined, CompilationParams params, Object serverFunctionManager) throws Throwable {
        var lk = MethodHandles.lookup();
        defined.dataClass().getDeclaredMethod("load",
            params.codeFieldStorage().minecraftServerType.resolveConstantDesc(lk),
            params.codeFieldStorage().serverFunctionManagerType.resolveConstantDesc(lk)
        ).invoke(null, minecraftServer, serverFunctionManager);
    }

    /**
     * Binds every compiled function to a slot of a generated dispatcher (see {@link DispatcherGenerator}).
     * A failure only sends the affected functions to vanilla (their batch is linked, so compiled callers keep
     * working). Returns the number registered.
     */
    private int registerAll(DefinedClasses defined, Map<DataLocation, BatchClassPlanner.PlacedFunction> placements, CompilationParams params) {
        var stackType = params.commandSourceStackType();
        var argsType = params.codeFieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND);
        var byBatch = new java.util.TreeMap<Integer, List<DataLocation>>();
        for(var entry : placements.entrySet()) byBatch.computeIfAbsent(entry.getValue().batchIndex(), k -> new ArrayList<>()).add(entry.getKey());
        int registered = 0;
        for(var batch : byBatch.entrySet()) {
            checkCancelled();
            int batchIndex = batch.getKey();
            var batchClass = defined.batches().get(batchIndex);
            var declared = new HashSet<String>();
            for(var method : batchClass.getDeclaredMethods()) declared.add(method.getName());
            var members = new ArrayList<DataLocation>(batch.getValue().size());
            for(var key : batch.getValue()) {
                if(declared.contains(placements.get(key).baseName())) members.add(key);
                else loadFailed(key, new NoSuchMethodException(batchClass.getName() + "." + placements.get(key).baseName()));
            }
            members.sort(Comparator.comparing(DataLocation::toString));
            var batchDesc = batchClass.describeConstable().orElseThrow();
            for(int from = 0, group = 0; from < members.size(); from += DispatcherGenerator.GROUP_SIZE, group++) {
                var slice = members.subList(from, Math.min(members.size(), from + DispatcherGenerator.GROUP_SIZE));
                try {
                    var names = new ArrayList<String>(slice.size());
                    for(var key : slice) names.add(placements.get(key).baseName());
                    var name = DispatcherGenerator.binaryName(batchIndex, group);
                    var bytes = DispatcherGenerator.generate(name, batchDesc, names, stackType, argsType);
                    dumpClassFile(name, bytes);
                    var constructor = defined.loader().define(name, bytes).getConstructor(int.class);
                    for(int i = 0; i < slice.size(); i++) {
                        var id = toIdentifier(slice.get(i));
                        handles.put(id, new CompiledFunction.Entry(id, (CompiledFunction) constructor.newInstance(i)));
                        registered++;
                    }
                } catch(CompileCancelledException e) {
                    throw e;
                } catch(Throwable e) {
                    if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) throw vme;
                    for(var key : slice) {
                        if(!handles.containsKey(toIdentifier(key))) loadFailed(key, e);
                    }
                }
            }
        }
        return registered;
    }

    private void loadFailed(DataLocation key, Throwable e) {
        diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_LOAD_FAILED,
            "Failed to create function handle (" + key + "), falling back to vanilla", null, key, null, e));
        LOGGER.warn("Failed to create function handle ({}), falling back to vanilla for this function", key, e);
    }

    private void dumpClassFile(String name, byte[] data) {
        if(!DEBUG_DUMP_CLASS_FILES) return;
        ensureGeneratedDir();
        try {
            Files.write(generatedDir.resolve(name + ".class"), data);
        } catch(Exception e) {
            LOGGER.warn("Failed to dump class file {}", name, e);
        }
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

        // ── Sources + cache probe ─────────────────────────────────────────────
        // The hash covers every codegen input (see computeGlobalCacheHash), so a hit needs no parsing at all.
        checkCancelled();
        var startParse = System.nanoTime();
        var rawSources = readFunctionSources();
        checkCancelled();
        var globalHash = computeGlobalCacheHash(rawSources);
        // Every loaded function that ends up without a compiled method runs via vanilla; the cache manifest
        // lists them so a later hit can tell them apart from functions added since.
        var allParsedIds = Set.copyOf(rawSources.keySet());

        // Only use cache when debug dumps are disabled (cache stores production bytecode only)
        if(!DEBUG_DUMP_INSTRUCTIONS) {
            setState(State.LOADING);
            try {
                var cached = tryLoadFromCache(globalHash, allParsedIds);
                if(cached != null) {
                    checkCancelled();
                    LOGGER.info("Cache hit — loading {} functions across {} batch class(es) from disk (skipping compilation)",
                        cached.placements().size(), cached.batchBytes().size());
                    var startLoad = System.nanoTime();
                    if(!defineAndLoadFromBytes(cached.batchBytes(), cached.placements(), cached.dataClasses(), params, serverFunctionManager, allParsedIds.size())) return;
                    LOGGER.info("Loaded compiled functions from cache in {}ms", (int)((System.nanoTime() - startLoad) / 1_000_000d));

                    LOGGER.info("[Summary] {} function(s) found, {} parsed, {} compiled (from cache)",
                        totalFunctionCount, allParsedIds.size(), handles.size());
                    finishLoading(autoEnable);
                    return;
                }
            } catch(CompileCancelledException e) {
                throw e;
            } catch(Throwable e) {
                if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) throw vme;
                LOGGER.warn("Cache read failed, falling back to full compilation", e);
            }
        }

        // ── Parse (cache miss) ────────────────────────────────────────────────
        setState(State.PARSING);
        var functionSources = FunctionCodeGenerator.loadFunctionSources(rawSources, diagnostics);
        rawSources = null;
        checkCancelled();
        var definedFunctions = FunctionCodeGenerator.parseFunctions(functionSources, lookup, diagnostics, this::isCancelled);
        functionSources = null;
        checkCancelled();
        var parseTime       = (System.nanoTime() - startParse) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Parsed functions ({}->{}) in {}ms",
            totalFunctionCount, definedFunctions.size(), (int)(parseTime * 1000));

        int parsedFunctionCount  = definedFunctions.size();
        int compiledCommandCount = definedFunctions.values().stream().mapToInt(List::size).sum();

        // ── Compile (cache miss path) ─────────────────────────────────────────
        //
        // Batches are planned from command counts BEFORE anything compiles, so every function's
        // (batch class, method) is known while call sites are emitted. Each batch is one pool task:
        // compile its functions chunk by chunk, assemble, verify, isolate failures (see BatchCompiler).
        // A function that fails anywhere keeps its slot as a vanilla-dispatch stub, so no caller
        // ever links to a missing or broken method. Heap stays bounded by the batches in flight.
        setState(State.COMPILING);
        LOGGER.info("Compiling functions");

        var startCompile      = System.nanoTime();
        boolean keepLowLevel  = DEBUG_DUMP_INSTRUCTIONS || DEBUG_GENERATE_GRAPH;
        ConcurrentHashMap<String, List<BytecodeInstruction>> lowLevelFunctions = keepLowLevel ? new ConcurrentHashMap<>() : null;
        ConcurrentHashMap<DataLocation, List<Instruction>> debugCompiledMap  = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;
        ConcurrentHashMap<DataLocation, List<Instruction>> debugOptimizedMap = DEBUG_DUMP_INSTRUCTIONS ? new ConcurrentHashMap<>() : null;

        var commandCounts = new HashMap<DataLocation, Integer>(definedFunctions.size());
        for(var entry : definedFunctions.entrySet()) commandCounts.put(entry.getKey(), entry.getValue().size());
        var planner = BatchClassPlanner.plan(commandCounts, LowPriorityCompileExecutor.parallelism());
        var env = new FunctionCodeGenerator.Environment(params, planner, planner.functions(),
            new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder(),
            lowLevelFunctions, debugCompiledMap, debugOptimizedMap);
        var batchCompiler = new BatchCompiler(env, planner, definedFunctions, diagnostics, generatedDir.resolve("failed_functions"));

        int batchCount = planner.batchClasses().size();
        var batches = new BatchCompiler.Batch[batchCount];
        // Heaviest batches first, so one big batch doesn't start last and leave the other workers idle.
        var order = new ArrayList<Integer>(batchCount);
        var batchWeight = new long[batchCount];
        for(int i = 0; i < batchCount; i++) {
            order.add(i);
            for(var key : planner.batchMembers().get(i)) batchWeight[i] += commandCounts.get(key);
        }
        order.sort(Comparator.comparingLong((Integer i) -> batchWeight[i]).reversed());
        LOGGER.info("[Compile] {} function(s) planned into {} batch class(es), parallelism={}",
            planner.functions().size(), batchCount, LowPriorityCompileExecutor.parallelism());
        var batchesDone = new AtomicInteger(0);
        try(var executor = LowPriorityCompileExecutor.newScope()) {
            for(int bi : order) {
                executor.execute(() -> {
                    if(isCancelled()) return;
                    var batchStart = System.nanoTime();
                    var batch = batchCompiler.build(bi, this::isCancelled);
                    if(batch == null) return;
                    batches[bi] = batch;
                    LOGGER.info("[Compile] batch {}/{} done: {} function(s) ({} compiled), {} KB, largest method {} bytes, {}ms",
                        batchesDone.incrementAndGet(), batchCount, planner.batchMembers().get(bi).size(), batch.compiled().size(),
                        batch.bytes().length / 1024, batch.maxMethodLength(), (System.nanoTime() - batchStart) / 1_000_000);
                });
            }
        }
        checkCancelled();
        definedFunctions.clear();

        var batchBytes = new ArrayList<byte[]>(batchCount);
        var compiled = new HashMap<DataLocation, BatchClassPlanner.PlacedFunction>(planner.functions().size());
        long totalFnFileSize = 0;
        int maxMethodLength = 0;
        for(int i = 0; i < batchCount; i++) {
            if(batches[i] == null) throw new IllegalStateException("Batch " + i + " failed to build (see log)");
            batchBytes.add(batches[i].bytes());
            compiled.putAll(batches[i].compiled());
            totalFnFileSize += batches[i].bytes().length;
            maxMethodLength = Math.max(maxMethodLength, batches[i].maxMethodLength());
        }
        var compileTime = (System.nanoTime() - startCompile) / 1_000_000_000d;
        LOGGER.info("[Compile] done in {}ms: {} of {} function(s) compiled, {} batch class(es), largest generated method {} bytes",
            (int)(compileTime * 1000), compiled.size(), planner.functions().size(), batchCount, maxMethodLength);

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

        // ── Load: define + link everything in a fresh loader, then register ──
        LOGGER.info("Loading functions");
        setState(State.LOADING);
        var startLoader = System.nanoTime();

        DefinedClasses defined;
        try {
            defined = defineGenerated(batchBytes, () -> params.codeFieldStorage().generate(new HashMap<>()),
                (index, error) -> batchCompiler.repairUnlinkable(index, batchBytes.get(index), this, compiled, error));
            initDataClass(defined, params, serverFunctionManager);
        } catch(CompileCancelledException e) {
            throw e;
        } catch(Throwable e) {
            if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) throw vme;
            if(e instanceof DiagnosticException de) diagnostics.addDiagnostic(de.toDiagnostic());
            LOGGER.error("Failed to load generated classes", e);
            setState(State.FAILED);
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
        var startHandles = System.nanoTime();
        int registered = registerAll(defined, compiled, params);
        var handleLoadTime = (System.nanoTime() - startHandles) / 1_000_000_000d;
        if(DEBUG_PRINT_TIMINGS) LOGGER.info("Loaded handles in {}ms", (int)(handleLoadTime * 1000));

        // Persist only what actually linked, and only after it did.
        if(!DEBUG_DUMP_INSTRUCTIONS) {
            try {
                var fallbackFunctions = new HashSet<>(allParsedIds);
                fallbackFunctions.removeAll(compiled.keySet());
                saveToCache(globalHash, batchBytes, compiled, fallbackFunctions, defined.dataClasses());
                LOGGER.info("Saved {} compiled functions across {} batch class(es) to cache ({} vanilla-fallback)",
                    compiled.size(), batchBytes.size(), fallbackFunctions.size());
            } catch(CompileCancelledException e) {
                throw e;
            } catch(Exception e) {
                LOGGER.warn("Failed to save compilation cache, compilation will continue", e);
            }
        }

        // ── Summary (local log only) ──────────────────────────────────────────
        LOGGER.info("[Summary] {} function(s) found, {} parsed, {} compiled, {} registered; {} command(s), {} -> {} instruction(s) after optimization, {} KB of function classes; parse {}ms, compile {}ms, class load {}ms, handles {}ms",
            totalFunctionCount, parsedFunctionCount, compiled.size(), registered,
            compiledCommandCount, env.preOptInstructions().sum(), env.postOptInstructions().sum(), totalFnFileSize / 1024,
            (int)(parseTime * 1000), (int)(compileTime * 1000), (int)(classLoadTime * 1000), (int)(handleLoadTime * 1000));
        writeDiagnosticsReport();

        finishLoading(autoEnable);
    }

    /**
     * Writes every diagnostic of this compilation to {@code generated/diagnostics.tsv} (kind, id, function, message,
     * command text for parse errors), so the commands that fall back to vanilla can be looked up per function.
     */
    private void writeDiagnosticsReport() {
        try {
            ensureGeneratedDir();
            var report = new StringBuilder("kind\tid\tfunction\tmessage\tcommand\n");
            for(var diagnostic : diagnostics.getDiagnostics()) {
                report.append(diagnostic.kind()).append('\t').append(diagnostic.diagnosticId()).append('\t').append(diagnostic.source()).append('\t')
                    .append(tsvField(diagnostic.message())).append('\t').append(diagnostic.extra() == null ? "" : tsvField(diagnostic.extra())).append('\n');
            }
            Files.writeString(generatedDir.resolve("diagnostics.tsv"), report);
        } catch(Exception e) {
            LOGGER.warn("Failed to write diagnostics.tsv", e);
        }
    }

    private static String tsvField(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Records the state to expose once {@link #publish} runs on the server thread. */
    protected final void finishLoading(boolean autoEnable) {
        checkCancelled();
        LOGGER.info(autoEnable ? "Compiled functions loaded, switching over once published" : "Compiled functions are ready to be enabled");
        readyState = autoEnable ? State.READY_ON : State.READY_OFF;
    }

    public boolean hasFunctionLoaded(TIdentifier location) {
        var entry = handles.get(location);
        return entry != null && entry.function != null;
    }

    /** Dispatch slot for {@code location}, or null when it was never compiled. */
    public CompiledFunction.@Nullable Entry getEntry(TIdentifier location) {
        return handles.get(location);
    }

    /** Id-keyed entry point (slow path); per-call dispatch should cache {@link #getEntry} instead. */
    public long runFunction(TIdentifier location, TCommandSourceStack stack, @Nullable TCompoundTag compound) {
        var entry = handles.get(location);
        var function = entry == null ? null : entry.function;
        if(function == null) return 1L << 32;
        try {
            return function.run(stack, compound);
        } catch(Throwable e) {
            return onRuntimeFailure(entry, e);
        }
    }

    /**
     * Handles an exception thrown out of compiled code. VM errors other than stack overflow are rethrown.
     * Otherwise the function is switched to vanilla for all later calls (its compiled code is broken, and
     * vanilla is the reference behaviour), one diagnostic is recorded and one log line written per function,
     * and this call reports failure.
     */
    public final long onRuntimeFailure(CompiledFunction.Entry entry, Throwable e) {
        if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) throw vme;
        if(entry.function == null) return 1L << 32;
        entry.function = null;
        if(e instanceof StackOverflowError) {
            LOGGER.error("Stack overflow in compiled function {}; running it through vanilla from now on", entry.id);
        } else {
            LOGGER.error("Compiled function {} threw; running it through vanilla from now on. Please report this bug.", entry.id, e);
        }
        diagnostics.addDiagnostic(new Diagnostic(DiagnosticKind.EXECUTION_ERROR, FUNCTION_EXECUTION_FAILED,
            "Compiled function failed at runtime and was switched to vanilla (" + entry.id + ")", null, new DataLocation(entry.id.toString()), null, e));
        return 1L << 32;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics.getDiagnostics();
    }
}
