package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticCollector;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticKind;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_CLASS_LOAD_FAILED;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.FUNCTION_LOAD_TOO_BIG;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.UNSPECIFIED_COMMAND_ERROR;

/**
 * Builds the batch classes of one compilation with per-function failure isolation.
 *
 * Every planned function keeps its (batch, method) slot for the whole compilation: call
 * sites are emitted as {@code invokestatic} against it before anyone knows whether the
 * callee compiles. A function that fails at any stage (compile, optimize, emit, class
 * build, verification, linking) is replaced by a same-named stub that runs it through
 * vanilla ({@link FunctionCodeGenerator#emitStub}), so callers stay valid without any
 * re-emission, and the stubbed function simply gets no dispatch handle.
 *
 * Classes are verified with the JDK verifier BEFORE they are defined: a class that fails
 * verification at link time can never be redefined under the same name in that loader.
 */
final class BatchCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    private final FunctionCodeGenerator.Environment env;
    private final BatchClassPlanner planner;
    private final Map<DataLocation, List<CommandParams>> functions;
    private final DiagnosticCollector diagnostics;
    private final @Nullable Path failedDumpDir;

    BatchCompiler(FunctionCodeGenerator.Environment env, BatchClassPlanner planner, Map<DataLocation, List<CommandParams>> functions,
                  DiagnosticCollector diagnostics, @Nullable Path failedDumpDir) {
        this.env = env;
        this.planner = planner;
        this.functions = functions;
        this.diagnostics = diagnostics;
        this.failedDumpDir = failedDumpDir;
    }

    /** A finished batch: its class bytes, the functions that are really compiled in it, and
     *  the length of its longest method's code. */
    record Batch(byte[] bytes, Map<DataLocation, BatchClassPlanner.PlacedFunction> compiled, int maxMethodLength) {}

    /**
     * Compiles every member of batch {@code index} and assembles a class that builds,
     * keeps every chunk under {@link FunctionCodeGenerator#MAX_METHOD_CODE_LENGTH} where the
     * commands allow it, and passes verification. Returns null if cancelled.
     */
    @Nullable Batch build(int index, BooleanSupplier cancelled) {
        var className = planner.batchClasses().get(index);
        var members = new LinkedHashMap<DataLocation, FunctionMethods>();
        for(var key : planner.batchMembers().get(index)) {
            if(cancelled.getAsBoolean()) return null;
            members.put(key, compileOrStub(key, null));
        }

        while(true) {
            if(cancelled.getAsBoolean()) return null;
            var methods = new ArrayList<MethodEmission>();
            for(var function : members.values()) methods.addAll(function.methods());

            byte[] bytes;
            try {
                bytes = ClassGenerator.createClass(className, methods);
            } catch(Throwable e) {
                rethrowFatal(e);
                if(!stubFailing(members, className, false)) stubAll(members, className, "class build failed: " + e, e);
                continue;
            }

            var model = ClassGenerator.parse(bytes);
            if(resplitOversized(members, model)) continue;

            var errors = ClassGenerator.verify(bytes);
            if(!errors.isEmpty()) {
                if(!stubFailing(members, className, true)) stubAll(members, className, "verification failed: " + errors.getFirst().getMessage(), errors.getFirst());
                continue;
            }

            var compiled = new HashMap<DataLocation, BatchClassPlanner.PlacedFunction>();
            for(var function : members.values()) {
                if(!function.stub()) compiled.put(function.id(), planner.placement(function.id()).withMacros(function.hasMacros()));
            }
            int maxMethodLength = 0;
            for(var method : model.methods()) maxMethodLength = Math.max(maxMethodLength, ClassGenerator.codeLength(method));
            return new Batch(bytes, compiled, maxMethodLength);
        }
    }

    private FunctionMethods compileOrStub(DataLocation key, @Nullable List<FunctionMethods.CommandRange> ranges) {
        var placement = planner.placement(key);
        try {
            var owner = planner.batchClasses().get(placement.batchIndex());
            return FrameCost.apply(FunctionCodeGenerator.compileFunction(env, key, functions.get(key), placement, owner, ranges), owner, placement.baseName());
        } catch(DiagnosticException e) {
            LOGGER.warn("Failed to compile function {} / {}", key, e.getMessage());
            diagnostics.addDiagnosticSafe(e.toDiagnostic());
        } catch(Throwable e) {
            rethrowFatal(e);
            LOGGER.error("Failed to compile function {}", key, e);
            diagnostics.addDiagnosticSafe(Diagnostic.compileError(UNSPECIFIED_COMMAND_ERROR, key, String.valueOf(e.getMessage()), e));
        }
        return emitStub(key);
    }

    /** Every function's methods go through {@link FrameCost#apply} before they reach a class. */
    private FunctionMethods emitStub(DataLocation key) {
        var placement = planner.placement(key);
        return FrameCost.apply(FunctionCodeGenerator.emitStub(env, key, placement), planner.batchClasses().get(placement.batchIndex()), placement.baseName());
    }

    private void stub(Map<DataLocation, FunctionMethods> members, DataLocation key, String diagnosticId, String reason, byte @Nullable [] dumpBytes) {
        LOGGER.warn("Function {} falls back to vanilla: {}", key, reason);
        diagnostics.addDiagnosticSafe(new Diagnostic(DiagnosticKind.LOAD_ERROR, diagnosticId, "Function (" + key + ") falls back to vanilla: " + reason, null, key, null, null));
        dump(key, reason, dumpBytes);
        members.put(key, emitStub(key));
    }

    /** The batch fails but none of its compiled members fails alone (never observed):
     *  stub all of them. Throws if the batch is already stubs only. */
    private void stubAll(Map<DataLocation, FunctionMethods> members, java.lang.constant.ClassDesc className, String reason, Throwable cause) {
        if(members.values().stream().allMatch(FunctionMethods::stub)) {
            throw new IllegalStateException("Stub-only batch " + className.displayName() + " is invalid: " + reason, cause);
        }
        LOGGER.error("Batch {} fails but none of its functions fails alone: {}", className.displayName(), reason);
        for(var key : List.copyOf(members.keySet())) {
            if(!members.get(key).stub()) stub(members, key, FUNCTION_CLASS_LOAD_FAILED, "in batch " + className.displayName() + ": " + reason, null);
        }
    }

    /**
     * Builds each compiled member alone (verifying it too when {@code verify}) and stubs
     * the ones that fail. Returns false if no member failed alone.
     */
    private boolean stubFailing(Map<DataLocation, FunctionMethods> members, java.lang.constant.ClassDesc className, boolean verify) {
        boolean any = false;
        for(var key : List.copyOf(members.keySet())) {
            var function = members.get(key);
            if(function.stub()) continue;
            byte[] alone = null;
            String failure = null;
            String id = FUNCTION_CLASS_LOAD_FAILED;
            try {
                alone = ClassGenerator.createClass(className, function.methods());
                if(verify) {
                    var errors = ClassGenerator.verify(alone);
                    if(!errors.isEmpty()) failure = "verification failed: " + errors.getFirst().getMessage();
                }
            } catch(Throwable e) {
                rethrowFatal(e);
                failure = "class build failed: " + e;
                if(String.valueOf(e.getMessage()).contains("Code length")) id = FUNCTION_LOAD_TOO_BIG;
            }
            if(failure != null) {
                stub(members, key, id, failure, alone);
                any = true;
            }
        }
        return any;
    }

    /**
     * Re-splits every chunk whose real code length exceeds the JIT limit (cost() is only an
     * estimate) and recompiles its function with the finer ranges. Returns true if any
     * function changed.
     */
    private boolean resplitOversized(Map<DataLocation, FunctionMethods> members, ClassModel model) {
        var byMethod = new HashMap<String, FunctionMethods>();
        for(var function : members.values()) {
            for(var method : function.methods()) byMethod.put(method.name(), function);
        }
        var newRanges = new LinkedHashMap<DataLocation, List<FunctionMethods.CommandRange>>();
        for(MethodModel method : model.methods()) {
            int length = ClassGenerator.codeLength(method);
            if(length <= FunctionCodeGenerator.MAX_METHOD_CODE_LENGTH) continue;
            var name = method.methodName().stringValue();
            var function = byMethod.get(name);
            int chunk = function == null ? MethodEmission.NO_CHUNK : function.methods().stream()
                .filter(m -> m.name().equals(name)).findFirst().map(MethodEmission::chunkIndex).orElse(MethodEmission.NO_CHUNK);
            if(chunk == MethodEmission.NO_CHUNK || function.chunks().get(chunk).size() <= 1) {
                LOGGER.warn("Generated method {} is {} bytes and cannot be split further; HotSpot will not JIT-compile it", name, length);
                continue;
            }
            var ranges = newRanges.computeIfAbsent(function.id(), k -> new ArrayList<>(function.chunks()));
            var range = function.chunks().get(chunk);
            int at = ranges.indexOf(range);
            ranges.remove(at);
            ranges.addAll(at, FunctionCodeGenerator.splitRange(range, Math.max(2, Math.ceilDiv(length, FunctionCodeGenerator.TARGET_METHOD_CODE_LENGTH))));
        }
        for(var entry : newRanges.entrySet()) members.put(entry.getKey(), compileOrStub(entry.getKey(), entry.getValue()));
        return !newRanges.isEmpty();
    }

    // ── Last resort: HotSpot rejects a class the JDK verifier accepted ─────────

    /** True if {@code method} is one of the methods generated for the function with entry {@code base}. */
    static boolean belongsTo(MethodModel method, String base) {
        var name = method.methodName().stringValue();
        if(!name.startsWith(base)) return false;
        var rest = name.substring(base.length());
        var instanced = BatchClassPlanner.instancedMethodName("");
        return rest.isEmpty() || rest.startsWith("$") || rest.equals(instanced) || rest.startsWith(instanced + "$");
    }

    /**
     * Rewrites batch {@code index} after HotSpot refused to link it: links each member alone
     * in a throwaway loader and replaces the ones that fail (or, if none fails alone, every
     * member) with stubs. Returns the new bytes and removes stubbed functions from {@code compiled}.
     */
    byte[] repairUnlinkable(int index, byte[] bytes, ClassLoader parent, Map<DataLocation, BatchClassPlanner.PlacedFunction> compiled, Throwable linkError) {
        var model = ClassGenerator.parse(bytes);
        var name = planner.batchClasses().get(index).displayName();
        var members = planner.batchMembers().get(index).stream().filter(compiled::containsKey).toList();
        var failing = new ArrayList<DataLocation>();
        for(var key : members) {
            var base = planner.placement(key).baseName();
            var alone = ClassGenerator.rewriteClass(model, m -> !belongsTo(m, base), List.of());
            try {
                GeneratedClassLoader.link(new GeneratedClassLoader(parent), binaryName(index), alone);
            } catch(LinkageError e) {
                failing.add(key);
            }
        }
        if(failing.isEmpty()) failing.addAll(members);
        var stubs = new ArrayList<MethodEmission>();
        var bases = new ArrayList<String>();
        for(var key : failing) {
            var placement = planner.placement(key);
            bases.add(placement.baseName());
            stubs.addAll(emitStub(key).methods());
            compiled.remove(key);
            var reason = "HotSpot rejected batch " + name + ": " + linkError;
            LOGGER.warn("Function {} falls back to vanilla: {}", key, reason);
            diagnostics.addDiagnosticSafe(new Diagnostic(DiagnosticKind.LOAD_ERROR, FUNCTION_CLASS_LOAD_FAILED, "Function (" + key + ") falls back to vanilla: " + reason, null, key, null, linkError));
        }
        dump(failing.getFirst(), "link error: " + linkError, bytes);
        return ClassGenerator.rewriteClass(model, m -> bases.stream().anyMatch(base -> belongsTo(m, base)), stubs);
    }

    static String binaryName(int batchIndex) {
        return (BatchClassPlanner.BATCH_CLASS_PREFIX + batchIndex).replace('/', '.');
    }

    private void dump(DataLocation key, String reason, byte @Nullable [] bytes) {
        if(failedDumpDir == null) return;
        try {
            Files.createDirectories(failedDumpDir);
            var safeName = (key.namespace() + "__" + key.path()).replace('/', '_');
            Files.writeString(failedDumpDir.resolve(safeName + ".txt"), key + "\n" + reason + "\n");
            if(bytes != null) Files.write(failedDumpDir.resolve(safeName + ".class"), bytes);
        } catch(Exception e) {
            LOGGER.warn("Failed to dump diagnostic info for {}", key, e);
        }
    }

    private static void rethrowFatal(Throwable e) {
        if(e instanceof FunctionCompilerClassLoader.CompileCancelledException cancelled) throw cancelled;
        if(e instanceof VirtualMachineError vme && !(e instanceof StackOverflowError)) throw vme;
    }

    /**
     * Defines generated classes. A fresh instance per load attempt: a class name can be
     * defined only once per loader, so retrying after a failure (cache hit that doesn't
     * link, HotSpot rejecting a batch) needs a new loader.
     */
    static final class GeneratedClassLoader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }

        GeneratedClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }

        /** Defines and links (verifies) {@code bytes}; batch classes have no static initializer. */
        static Class<?> link(GeneratedClassLoader loader, String name, byte[] bytes) {
            loader.define(name, bytes);
            try {
                return Class.forName(name, true, loader);
            } catch(ClassNotFoundException e) {
                throw new IllegalStateException("Class " + name + " vanished after definition", e);
            }
        }
    }
}
