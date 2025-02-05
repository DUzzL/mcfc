package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_1;

import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.ExecutionContextExtension;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerFunctionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

@Mixin(CommandFunction.FunctionEntry.class)
public class FunctionEntryMixin {

    @Shadow @Final private CommandFunction.CacheableFunction function;

    @Inject(method = "execute", at = @At("HEAD"))
    private void execute(ServerFunctionManager serverFunctionManager, CommandSourceStack commandSourceStack, Deque<ServerFunctionManager.QueuedCommand> deque, int i, int j, @Nullable ServerFunctionManager.TraceCallbacks traceCallbacks, CallbackInfo ci) {
        if(Profiler.isProfilerEnabled()) {
            var optFunc = function.get(serverFunctionManager);
            if(optFunc.isPresent()) {
                var ext = (ExecutionContextExtension) serverFunctionManager.context;
                if(ext != null) {
                    var func = optFunc.get();
                    if(!ext.mcfc$stackDepth().isEmpty() && ext.mcfc$stackDepth().topInt() == j) {
                        Profiler.exit();
                    }else {
                        ext.mcfc$stackDepth().push(j);
                    }
                    Profiler.enterVanilla(func.getId().toString());
                }
            }
        }
    }

}
