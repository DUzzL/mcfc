package dev.epicpix.minecraftfunctioncompiler.mixin.v1_19_4;

import com.mojang.brigadier.context.CommandContext;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_19_4.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_19_4.FunctionCompilerMain;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
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

import java.util.Collection;
import java.util.Map;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.AUTO_SWITCH_FUNCTIONS;

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

    @Inject(method = "execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager$TraceCallbacks;)I", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction commandFunction, CommandSourceStack commandSourceStack, ServerFunctionManager.TraceCallbacks traceCallbacks, CallbackInfoReturnable<Integer> cir) {
        if(classLoader != null && classLoader.isUseReady()) {
            if(classLoader.hasFunctionLoaded(commandFunction.getId())) {
                int res = (int) classLoader.runFunction(commandFunction.getId(), commandSourceStack, null);
                commandSourceStack.onCommandComplete(new CommandContext<>(commandSourceStack, "function " + commandFunction.getId(), Map.of(), null, null, null, null, null, null, false), true, res);
                cir.setReturnValue(res);
            }
        }
    }

}
