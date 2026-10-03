package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.CompileConstants;
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.loader.CompilationParams;
import dev.epicpix.minecraftfunctioncompiler.loader.CompiledFunction;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.BLOCKING_COMPILATION;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callCompoundTag_get;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callTag_stringify;

public class FunctionClassLoader extends FunctionCompilerClassLoader<MinecraftServer, Identifier, CommandSourceStack, CompoundTag> {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private static final String COMPILER_VERSION = computeCompilerVersion();
    /** Server thread only. */
    private static int lastGeneration;

    /** Distinct per loader (never 0); keys the per-function dispatch caches without referencing the loader. */
    public final int generation = ++lastGeneration;

    private final ServerFunctionManager serverFunctionManager;
    /** Ids of the functions vanilla loaded in this reload (immutable key set of the library map). */
    private final Set<Identifier> loadedFunctionIds;
    /** Lines vanilla parsed for each function of this reload's library (see {@link FunctionSourceCapture}). */
    private final Map<Identifier, List<String>> capturedSources;

    /** Captures the post-reload inputs; must be constructed on the server thread. */
    public FunctionClassLoader(ClassLoader parent, MinecraftServer minecraftServer, ServerFunctionManager serverFunctionManager) {
        this(parent, minecraftServer, serverFunctionManager, serverFunctionManager.library.getFunctions().keySet());
    }

    private FunctionClassLoader(ClassLoader parent, MinecraftServer minecraftServer, ServerFunctionManager serverFunctionManager, Set<Identifier> loadedFunctionIds) {
        super(parent, FabricLoader.getInstance().getGameDir().resolve("generated"), minecraftServer, loadedFunctionIds.size());
        this.serverFunctionManager = serverFunctionManager;
        this.loadedFunctionIds = loadedFunctionIds;
        this.capturedSources = ((FunctionSourceCapture) serverFunctionManager.library).mcfc$capturedSources();
    }

    /**
     * "modVersion|code fingerprint/mcVersion/commit": invalidates the cache whenever MCFC or Minecraft change,
     * including rebuilt development jars and, in development runs, class directories (where version and commit
     * are constants), so codegen changes never load bytecode cached by an older build.
     */
    private static String computeCompilerVersion() {
        var loader = FabricLoader.getInstance();
        var sb = new StringBuilder();
        var codePaths = new LinkedHashSet<Path>();
        loader.getModContainer("minecraftfunctioncompiler").ifPresentOrElse(c -> {
            sb.append(c.getMetadata().getVersion().getFriendlyString());
            codePaths.addAll(c.getOrigin().getPaths());
        }, () -> sb.append("unknown"));
        // The compiler itself lives in :common, which is a separate class directory in development runs.
        for(var cls : new Class<?>[]{FunctionCompilerClassLoader.class, FunctionClassLoader.class}) {
            try {
                codePaths.add(Path.of(cls.getProtectionDomain().getCodeSource().getLocation().toURI()));
            } catch(Exception ignored) {
                // not a file-system code source; the mod origin still applies
            }
        }
        sb.append('|').append(fingerprint(codePaths));
        sb.append('/').append(loader.getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        sb.append('/').append(CompileConstants.versionCommit);
        return sb.toString();
    }

    /** SHA-256 over (path, size, mtime) of every jar and of every file under every class directory. */
    private static String fingerprint(Set<Path> paths) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for(var root : paths) {
                List<Path> files;
                if(Files.isDirectory(root)) {
                    try(var walk = Files.walk(root)) {
                        files = walk.filter(Files::isRegularFile).sorted().toList();
                    }
                } else if(Files.isRegularFile(root)) {
                    files = List.of(root);
                } else {
                    continue;
                }
                for(var file : files) {
                    md.update((file + "|" + Files.size(file) + "@" + Files.getLastModifiedTime(file).toMillis() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest(), 0, 12);
        } catch(Exception e) {
            // Without a fingerprint a rebuilt compiler could load stale cached bytecode: never match the cache.
            LOGGER.warn("Failed to fingerprint the compiler code, compilation cache disabled for this run", e);
            return "unfingerprinted-" + System.nanoTime();
        }
    }

    @Override
    protected String getCompilerVersion() {
        return COMPILER_VERSION;
    }

    @Override
    protected Map<DataLocation, List<String>> readFunctionSources() {
        var sources = new HashMap<DataLocation, List<String>>(loadedFunctionIds.size());
        for(var id : loadedFunctionIds) {
            var lines = capturedSources.get(id);
            if(lines != null) sources.put(new DataLocation(id.getNamespace(), id.getPath()), lines);
            else LOGGER.warn("No captured source for function {}; it runs through vanilla", id);
        }
        return sources;
    }

    @Override
    protected void runOnServerThread(Runnable task) {
        if(minecraftServer.isSameThread()) task.run();
        else minecraftServer.execute(task);
    }

    @Override
    protected Identifier toIdentifier(DataLocation key) {
        return Identifier.fromNamespaceAndPath(key.namespace(), key.path());
    }

    /**
     * Starts compiling this reload's functions. Returns immediately (unless blocking compilation is configured);
     * {@code onComplete} runs on the server thread when this compile finished, failed, or was superseded.
     */
    public void loadFunctions(boolean autoEnable, Runnable onComplete) {
        boolean blocking = BLOCKING_COMPILATION || FunctionCompilerReporter.isTestEnvironment();
        startCompile(() -> compile(autoEnable), blocking, onComplete);
    }

    private void compile(boolean autoEnable) {
        if(totalFunctionCount() == 0) {
            finishLoading(autoEnable);
            return;
        }
        try {
            RegistryMapping.updateRegistryMaps(minecraftServer, serverFunctionManager);
        } catch(IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        checkCancelled();
        LOGGER.info("Starting compilation");
        var lookup = new IArgumentLookup() {
            @Override
            public List<DataLocation> lookupFunctions(TaggableLocation functions) {
                if(functions.isTag()) {
                    var tags = DataRegistries.FUNCTION_TAGS.get(functions.location());
                    return tags.orElseGet(List::of);
                }else if(loadedFunctionIds.contains(Identifier.fromNamespaceAndPath(functions.location().namespace(), functions.location().path()))) {
                    return List.of(functions.location());
                }else {
                    return List.of();
                }
            }
        };
        loadFunctionClasses(lookup, createCompilationParams(), serverFunctionManager, autoEnable);
    }

    /**
     * Vanilla->compiled entry. Charges the recursion budget, restores it (and vanilla's current execution
     * context, which compiled code clears around nested vanilla calls) on every exit, and turns exceptions
     * into a per-function switch to vanilla.
     */
    public long invoke(CompiledFunction.Entry entry, CompiledFunction function, CommandSourceStack stack, @Nullable CompoundTag arguments) {
        int savedDepth = FunctionCompilerRecursionGuard.depth;
        FunctionCompilerRecursionGuard.depth = savedDepth + FunctionCompilerRecursionGuard.VANILLA_ENTRY_COST;
        var savedContext = Commands.CURRENT_EXECUTION_CONTEXT.get();
        try {
            return function.run(stack, arguments);
        } catch(Throwable e) {
            Commands.CURRENT_EXECUTION_CONTEXT.set(savedContext);
            return onRuntimeFailure(entry, e);
        } finally {
            FunctionCompilerRecursionGuard.depth = savedDepth;
        }
    }

    public static CompilationParams createCompilationParams() {
        return new CompilationParams(
            new CodeFieldStorage(ServerFunctionManager.class.describeConstable().orElseThrow(), MinecraftServer.class.describeConstable().orElseThrow(), CodeFieldStorageMapImpl.INSTANCE),
            (emitter, key, errorLabel) -> {
                var cw = emitter.codeWriter();
                var tag = callCompoundTag_get(cw, emitter.getStatic("FunctionCompound"), new BytecodeValue.StringValue(key));
                if(errorLabel != null) {
                    cw.conditionalJump(JumpCondition.EQ, tag, new BytecodeValue.NullValue(), errorLabel);
                }
                return callTag_stringify(cw, tag);
            },
            CommandSourceStack.class.describeConstable().orElseThrow()
        );
    }
}
