package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_4;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.epicpix.minecraftfunctioncompiler.v1_20_4.FunctionCompilerMain.functionClassLoader;

@Mixin(CallFunction.class)
public class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow @Final private CommandResultCallback resultCallback;

    @Inject(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/execution/ExecutionContext;incrementCost()V", shift = At.Shift.AFTER), cancellable = true)
    public void execute(T source, ExecutionContext<T> executionContext, Frame frame, CallbackInfo ci) {
        if(source instanceof CommandSourceStack stack && functionClassLoader != null && functionClassLoader.isUseReady()) {
            var id = ((CallFunction<?>) (Object) this).function.id();
            if(functionClassLoader.hasFunctionLoaded(id)) {
                long res = functionClassLoader.runFunction(id, stack, null);
                if(res >> 32 < 2) {
                    if (res >> 32 == 1) resultCallback.onFailure();
                    else resultCallback.onSuccess((int) res);
                }
                ci.cancel();
            }
        }
    }

}
