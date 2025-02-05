package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_6;

import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_20_6.ExecutionContextExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExecutionContext.class)
public class ExecutionContextMixin<T> implements ExecutionContextExtension {

    @Shadow private int currentFrameDepth;
    @Unique private final IntArrayList depthStack = new IntArrayList();

    public IntArrayList mcfc$stackDepth() {
        return depthStack;
    }

    @Redirect(method = "runCommandQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/execution/CommandQueueEntry;frame()Lnet/minecraft/commands/execution/Frame;"))
    public Frame runCommandQueueFrame(CommandQueueEntry<T> instance) {
        if(Profiler.isProfilerEnabled()) {
            var previousDepth = currentFrameDepth;
            var newDepth = instance.frame().depth();
            if(newDepth < previousDepth) {
                while(!depthStack.isEmpty()) {
                    int expectedNew = depthStack.topInt();
                    if(expectedNew < newDepth) break;
                    depthStack.popInt();
                    Profiler.exit();
                }
            }
        }
        return instance.frame();
    }

    @Inject(method = "runCommandQueue", at = @At("RETURN"))
    public void runCommandQueue(CallbackInfo ci) {
        if(Profiler.isProfilerEnabled()) {
            for(int i = 0; i < depthStack.size(); i++) {
                Profiler.exit();
            }
        }
    }

}
