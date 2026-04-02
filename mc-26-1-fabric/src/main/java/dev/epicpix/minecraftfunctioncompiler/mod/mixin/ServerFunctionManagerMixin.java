package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.epicpix.minecraftfunctioncompiler.mod.FunctionDebugLogger;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements FunctionCompilationStarter {

    @Shadow public ServerFunctionLibrary library;
    @Unique @Nullable private FunctionClassLoader classLoader;
    @Shadow @Final public MinecraftServer server;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        mcfc$loadFunctions(true, () -> {});
    }

    @Override
    public void mcfc$loadFunctions(boolean autoEnable, Runnable onComplete) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), server, library);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this, autoEnable, onComplete);
        FunctionCompilerMain.functionClassLoader = classLoader;
    }

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CallbackInfo ci) {
        if(classLoader != null && classLoader.isUseReady()) {
            if(classLoader.hasFunctionLoaded(commandFunction.id())) {
                FunctionDebugLogger.setMcfcState(true);
                FunctionDebugLogger.recordCompiledCall(commandFunction.id());
                classLoader.runFunction(commandFunction.id(), commandSourceStack, null);
                ci.cancel();
                return;
            }
        }
        FunctionDebugLogger.recordCall(commandFunction.id(), true);
    }

}
