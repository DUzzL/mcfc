package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_2;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.FunctionCompilerMain;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.AUTO_SWITCH_FUNCTIONS;

import java.util.Collection;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements FunctionCompilationStarter {

    @Shadow public ServerFunctionLibrary library;
    @Unique private FunctionClassLoader classLoader;
    @Shadow @Final MinecraftServer server;

    @Inject(method = "executeTagFunctions", at = @At("HEAD"))
    public void tagStart(Collection<CommandFunction> collection, ResourceLocation resourceLocation, CallbackInfo ci) {
        if(Profiler.isProfilerEnabled() && !collection.isEmpty()) Profiler.enterVanilla("#" + resourceLocation);
    }

    @Inject(method = "executeTagFunctions", at = @At("TAIL"))
    public void tagEnd(Collection<CommandFunction> collection, ResourceLocation resourceLocation, CallbackInfo ci) {
        if(Profiler.isProfilerEnabled() && !collection.isEmpty()) Profiler.exit();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tickStart(CallbackInfo ci) {
        if(Profiler.isProfilerEnabled()) Profiler.checkResetStatus();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;executeTagFunctions(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V", ordinal = 1, shift = At.Shift.AFTER))
    public void tickEnd(CallbackInfo ci) {
        if(Profiler.isProfilerEnabled()) Profiler.collectEvents();
    }

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        var autoEnable = (FunctionCompilerMain.functionClassLoader == null || FunctionCompilerMain.functionClassLoader.getState() != FunctionCompilerClassLoader.State.READY_OFF) && AUTO_SWITCH_FUNCTIONS;
        mcfc$loadFunctions(autoEnable, () -> {});
    }

    @Override
    public void mcfc$loadFunctions(boolean autoEnable, Runnable onComplete) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), server, (FunctionCompilerClassLoader.SourceFileProvider) library);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this, autoEnable, onComplete);
        FunctionCompilerMain.functionClassLoader = classLoader;
    }

    @Inject(method = "execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction commandFunction, CommandSourceStack commandSourceStack, ServerFunctionManager.TraceCallbacks traceCallbacks, CompoundTag compoundTag, CallbackInfoReturnable<Integer> cir) {
        if(classLoader != null && classLoader.isUseReady()) {
            if(classLoader.hasFunctionLoaded(commandFunction.getId())) {
                long res = classLoader.runFunction(commandFunction.getId(), commandSourceStack, compoundTag);
                if(res >> 32 < 2) {
                    commandSourceStack.getReturnValueConsumer().accept((int) res);
                }
                cir.setReturnValue((int) res);
            }
        }
    }

}
