package dev.epicpix.minecraftfunctioncompiler.mod;

import net.minecraft.commands.execution.ExecutionContext;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla execution context that compiled code started (a nested function call or a fallback command) stopped
 * because it ran out of its command limit. Vanilla then drops the whole rest of the chain, so this unwinds every
 * compiled frame up to the vanilla->compiled entry ({@code FunctionClassLoader.invoke}), which stops the vanilla
 * context around it too. Without it compiled callers went on and started the next exhausting call (a recursion
 * that calls itself twice hung the server).
 */
public final class CommandLimitAbort extends RuntimeException {
    public static final CommandLimitAbort INSTANCE = new CommandLimitAbort();

    private CommandLimitAbort() {
        super("command limit reached", null, false, false);
    }

    /** Throws when {@code context} stopped at its command limit. */
    public static void checkStopped(@Nullable ExecutionContext<?> context) {
        if(context != null && context.commandQuota <= 0) throw INSTANCE;
    }

    /** Makes {@code context} stop before its next queued command, like a vanilla context at its limit. */
    public static void stop(@Nullable ExecutionContext<?> context) {
        if(context != null) context.commandQuota = 0;
    }
}
