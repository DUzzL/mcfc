package dev.epicpix.minecraftfunctioncompiler.mixin.v1_19_4;

import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_19_4.ExecutionContextExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerFunctionManager.ExecutionContext.class)
public class ExecutionContextMixin implements ExecutionContextExtension {

    @Shadow private int depth;
    @Unique private final IntArrayList depthStack = new IntArrayList();

    public IntArrayList mcfc$stackDepth() {
        return depthStack;
    }

    @Inject(method = "runTopCommand", at = @At(value = "HEAD"))
    public void runTopCommandEntry(CommandFunction commandFunction, CommandSourceStack commandSourceStack, CallbackInfoReturnable<Integer> cir) {
        if(Profiler.isProfilerEnabled()) {
            Profiler.enterVanilla(commandFunction.getId().toString());
        }
    }

    @Redirect(method = "runTopCommand", at = @At(value = "FIELD", target = "Lnet/minecraft/server/ServerFunctionManager$QueuedCommand;depth:I"))
    public int runTopCommandFrame(ServerFunctionManager.QueuedCommand instance) {
        if(Profiler.isProfilerEnabled()) {
            var previousDepth = depth;
            var newDepth = instance.depth;
            if(newDepth < previousDepth) {
                while(!depthStack.isEmpty()) {
                    int expectedNew = depthStack.topInt();
                    if(expectedNew < newDepth) break;
                    depthStack.popInt();
                    Profiler.exit();
                }
            }
        }
        return instance.depth;
    }

    @Inject(method = "runTopCommand", at = @At("RETURN"))
    public void runTopCommand(CommandFunction commandFunction, CommandSourceStack commandSourceStack, CallbackInfoReturnable<Integer> cir) {
        if(Profiler.isProfilerEnabled()) {
            Profiler.exit();
            for(int i = 0; i < depthStack.size(); i++) {
                Profiler.exit();
            }
        }
    }

}
