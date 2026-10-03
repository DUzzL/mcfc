package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.loader.CompiledFunction;

/**
 * Implemented on vanilla's PlainTextFunction (see PlainTextFunctionMixin): caches the compiled dispatch slot
 * of the function object, keyed by the generation of the loader that produced it, so vanilla->compiled dispatch
 * costs one int compare instead of a map lookup per call. A /reload or /mcfc recompile installs a new loader,
 * which invalidates every cached slot; installing it also calls {@link #mcfc$resetDispatchCache} on every
 * function of the library, so a slot cached under the old loader can't keep that loader's whole generation
 * (classes, dispatchers, data class) reachable.
 */
public interface CompiledDispatchCache {
    CompiledFunction.Entry mcfc$entry(FunctionClassLoader loader);

    void mcfc$resetDispatchCache();

    /** Slot for functions without compiled code. */
    CompiledFunction.Entry NOT_COMPILED = new CompiledFunction.Entry("<not compiled>", null);
}
