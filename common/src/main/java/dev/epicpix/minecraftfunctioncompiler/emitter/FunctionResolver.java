package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;

/**
 * Resolves compiled-function call targets during bytecode emission.
 *
 * Under the legacy model (1 class per .mcfunction) this was trivial: the class
 * name was derivable from the DataLocation alone. Under the batching model
 * multiple functions share a single container class with mangled method names,
 * so emitters must consult a resolver to produce correct invokestatic targets.
 *
 * Replaces the older `Set&lt;DataLocation&gt; compiledFunctions` channel for
 * resolution purposes; callers that only need membership use {@link #isCompiled}.
 */
public interface FunctionResolver {

    /** True if the function was successfully compiled and will be invokestatic-callable. */
    boolean isCompiled(DataLocation function);

    /**
     * Resolve the primary `run` entry point of a compiled function.
     * @return null if the function was not compiled.
     */
    @Nullable ResolvedTarget resolveRun(DataLocation function);

    record ResolvedTarget(ClassDesc batchClass, String methodName) {}
}
