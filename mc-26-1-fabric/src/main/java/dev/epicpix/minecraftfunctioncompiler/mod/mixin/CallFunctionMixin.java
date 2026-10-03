package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.mod.CompiledDispatchCache;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerRecursionGuard;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionDebugLogger;
import dev.epicpix.minecraftfunctioncompiler.mod.ServerThreadGate;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain.functionClassLoader;

@Mixin(value = CallFunction.class, priority = 990)
public class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow @Final private CommandResultCallback resultCallback;
    @Shadow @Final private boolean returnParentFrame;
    @Shadow @Final public InstantiatedFunction<T> function;

    @Inject(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/execution/ExecutionContext;incrementCost()V", shift = At.Shift.AFTER), cancellable = true)
    public void execute(T source, ExecutionContext<T> executionContext, Frame frame, CallbackInfo ci) {
        // Compiled bodies use server-thread-only state; any other thread runs vanilla's interpreter.
        if(!ServerThreadGate.onServerThread()) return;
        var loader = functionClassLoader;
        if(loader != null && source instanceof CommandSourceStack stack && function instanceof CompiledDispatchCache cache && loader.isUseReady()) {
            var entry = cache.mcfc$entry(loader);
            var compiled = entry.function;
            if(compiled != null && !FunctionCompilerRecursionGuard.atLimit()) {
                if(FunctionDebugLogger.enabled) FunctionDebugLogger.recordCompiledCall(function.id());
                long res = loader.invoke(entry, compiled, stack, null);
                if(res >> 32 < 2) {
                    if (res >> 32 == 1) resultCallback.onFailure();
                    else resultCallback.onSuccess((int) res);
                    Frame.FrameControl frameControl = returnParentFrame ? frame.frameControl() : executionContext.frameControlForDepth(frame.depth() + 1);
                    frameControl.discard();
                }
                ci.cancel();
                return;
            }
        }
        if(FunctionDebugLogger.enabled && source instanceof CommandSourceStack) {
            FunctionDebugLogger.recordCall(function.id(), true);
        }
    }

}
