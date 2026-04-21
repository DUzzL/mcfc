package dev.epicpix.minecraftfunctioncompiler.mod;

// Thread-local fuse for recursive datapack function calls.
//
// Compiled function bodies invoke each other through Java method calls,
// so a deeply tail-recursive datapack (e.g. a raycast loop) translates
// directly to deep Java recursion and blows the thread stack.
// When depth reaches MAX_DEPTH the mixins refuse to short-circuit into
// compiled code and let vanilla's iterative ExecutionContext queue pick
// the call up, which grows no further Java stack.
public final class FunctionCompilerRecursionGuard {

    // Each compiled-call frame plus the vanilla dispatch wrappers around it
    // costs ~30–50 Java frames; a 512 KiB thread stack fits roughly 5000
    // frames, so 64 levels keeps us well under the cliff with headroom for
    // nested brigadier parsing work at the deepest frame.
    public static final int MAX_DEPTH = 64;

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private FunctionCompilerRecursionGuard() {}

    public static boolean atLimit() {
        return DEPTH.get()[0] >= MAX_DEPTH;
    }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        DEPTH.get()[0]--;
    }
}
