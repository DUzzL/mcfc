package dev.epicpix.minecraftfunctioncompiler.mod;

/**
 * The physical server thread, bound when {@code MinecraftServer} is constructed.
 *
 * <p>Compiled function bodies use server-thread-only state: the static recursion budget
 * ({@link FunctionCompilerRecursionGuard#depth}), the per-function dispatch slots, the shared
 * {@link ScoreRef}/{@link ObjectiveRef} caches and the debug counters. A server mod that runs
 * vanilla command execution on other threads (Fabrolia's island threads, for example) must get
 * vanilla's interpreter there, so every compiled-code entry point checks this gate first.
 */
public final class ServerThreadGate {

    private static volatile Thread serverThread;

    private ServerThreadGate() {}

    public static void bind(Thread thread) {
        serverThread = thread;
    }

    /** True only on the exact thread the running server was constructed with. */
    public static boolean onServerThread() {
        return Thread.currentThread() == serverThread;
    }
}
