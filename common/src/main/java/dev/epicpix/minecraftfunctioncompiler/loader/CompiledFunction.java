package dev.epicpix.minecraftfunctioncompiler.loader;

import org.jspecify.annotations.Nullable;

/**
 * A compiled function's vanilla->compiled entry point. Implemented by generated per-batch dispatchers
 * ({@link DispatcherGenerator}): an interface call, a {@code tableswitch} on the function's slot and a direct
 * {@code invokestatic} of the entry point. Arguments are the platform's command source stack and macro
 * compound (or null).
 */
@FunctionalInterface
public interface CompiledFunction {
    long run(Object stack, @Nullable Object arguments);

    /**
     * Per-function dispatch slot shared by the loader's id map and the per-function-object
     * caches on the platform side. {@link #function} is nulled when the compiled code failed at
     * runtime; from then on the function runs through vanilla. Server thread only.
     */
    final class Entry {
        public final Object id;
        public @Nullable CompiledFunction function;

        public Entry(Object id, @Nullable CompiledFunction function) {
            this.id = id;
            this.function = function;
        }
    }
}
