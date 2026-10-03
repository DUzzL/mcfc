package dev.epicpix.minecraftfunctioncompiler.mod;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.util.profiling.Profiler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiled code's call of a function through vanilla's executor: callees without compiled code, and compiled
 * callees once {@link FunctionCompilerRecursionGuard} hands deep recursion back to vanilla.
 *
 * Matches vanilla's {@code function} command (FunctionCommand): the callee runs with the caller's callbacks
 * cleared, so its commands report nowhere, and only the callee's own frame return reaches the caller. Called
 * from generated code.
 */
public final class VanillaFunctionCall {
    private VanillaFunctionCall() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    /** Result when the callee finished without {@code return}: compiled code's "no return" sentinel. */
    public static final long NO_RETURN = 2L << 32;
    private static final long FAILURE = 1L << 32;

    /** Receives the callee frame's return. */
    private static final class ReturnCollector implements CommandResultCallback {
        long result = NO_RETURN;

        @Override
        public void onResult(boolean success, int value) {
            result = success ? Integer.toUnsignedLong(value) : FAILURE | Integer.toUnsignedLong(value);
        }
    }

    /**
     * Runs {@code function} to completion in a fresh vanilla execution context and returns the callee's return
     * in compiled encoding: {@code value} on success, {@code 1 << 32 | value} on failure, {@link #NO_RETURN} when
     * it fell through. A macro function whose arguments don't instantiate fails, like the vanilla command.
     */
    public static long call(ServerFunctionManager manager, CommandFunction<CommandSourceStack> function, CommandSourceStack stack, @Nullable CompoundTag arguments) {
        var collector = new ReturnCollector();
        var profiler = Profiler.get();
        profiler.push(() -> "function " + function.id());
        // Compiled code may run inside a vanilla context; the callee gets its own, like a nested
        // ServerFunctionManager.execute, so it runs to completion before compiled code continues.
        var outerContext = Commands.CURRENT_EXECUTION_CONTEXT.get();
        Commands.CURRENT_EXECUTION_CONTEXT.set(null);
        try {
            InstantiatedFunction<CommandSourceStack> instantiated = function.instantiate(arguments, manager.getDispatcher());
            var source = stack.clearCallbacks();
            Commands.executeCommandInContext(source, context -> context.queueNext(new CommandQueueEntry<>(
                new Frame(0, CommandResultCallback.EMPTY, context.frameControlForDepth(0)),
                new CallFunction<>(instantiated, collector, false).bind(source))));
        } catch(net.minecraft.commands.FunctionInstantiationException e) {
            return FAILURE;
        } catch(Exception e) {
            LOGGER.warn("Failed to execute function {}", function.id(), e);
        } finally {
            Commands.CURRENT_EXECUTION_CONTEXT.set(outerContext);
            profiler.pop();
        }
        return collector.result;
    }
}
