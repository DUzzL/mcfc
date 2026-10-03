package dev.epicpix.minecraftfunctioncompiler.mod;

// Stack fuse for compiled datapack function calls.
//
// Vanilla runs nested function calls through its iterative ExecutionContext queue, so
// recursion never grows the Java stack. Compiled function bodies call each other with
// invokestatic, so deep datapack recursion (raycast loops, …) becomes Java recursion.
//
// The counter estimates the Java stack, in 8-byte words, held by compiled frames (and the
// vanilla frames between a compiled call into vanilla and its re-entry). Every compiled call
// site (see mod/emitter/Emitter) charges the calling function's interpreted frame chain,
// a per-function constant computed after stack compilation (loader/FrameCost), so large
// function bodies use up the budget proportionally faster; every vanilla->compiled entry
// (FunctionClassLoader.invoke) charges VANILLA_ENTRY_COST. Once the budget is used up the
// call is routed through vanilla (VanillaFunctionCall), which executes the callee
// iteratively and grows no further compiled frames.
//
// A plain static int, not a ThreadLocal: compiled functions only ever run on the server
// thread (parallel datapack mode is disabled), and the generated code reads/writes it with
// one getstatic/putstatic per call. Vanilla->compiled entry restores the saved value in a
// finally, so an exception thrown out of compiled code can never leave the counter skewed.
public final class FunctionCompilerRecursionGuard {

    /**
     * Budget of compiled stack, in words: 512 KiB, half of the 1 MiB default thread stack the server thread
     * runs on (64-bit Linux/Windows/macOS). The rest stays free for the server's own frames below the first
     * function call, the deepest compiled function's frames (charged only once it calls further), and whatever
     * vanilla needs once the guard hands off.
     */
    public static final int MAX_DEPTH = 64 * 1024;
    /**
     * Cost of a vanilla->compiled entry, in words: vanilla's dispatch frames between a compiled call into
     * vanilla and the next compiled entry (VanillaFunctionCall, ExecutionContext, CallFunction, the mixin, the
     * loader's invoke and the generated dispatcher; brigadier's execute chain when the call comes from a
     * command), with a wide margin.
     */
    public static final int VANILLA_ENTRY_COST = 1024;

    /** Read and written by generated code; server thread only. */
    public static int depth;

    private FunctionCompilerRecursionGuard() {}

    public static boolean atLimit() {
        return depth >= MAX_DEPTH - VANILLA_ENTRY_COST;
    }
}
