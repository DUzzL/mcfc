package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_1;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.FunctionCompilerMain;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
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

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements FunctionCompilationStarter {

    @Shadow public ServerFunctionLibrary library;
    @Unique private FunctionClassLoader classLoader;
    @Shadow @Final MinecraftServer server;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        mcfc$loadFunctions(() -> {});
    }

    @Override
    public void mcfc$loadFunctions(Runnable onComplete) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), server, (FunctionCompilerClassLoader.SourceFileProvider) library);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this, onComplete);
        FunctionCompilerMain.functionClassLoader = classLoader;
    }

    @Inject(method = "execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager$TraceCallbacks;)I", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction commandFunction, CommandSourceStack commandSourceStack, ServerFunctionManager.TraceCallbacks traceCallbacks, CallbackInfoReturnable<Integer> cir) {
        if(classLoader != null && classLoader.isUseReady()) {
            if(classLoader.hasFunctionLoaded(commandFunction.getId())) {
                int res = (int) classLoader.runFunction(commandFunction.getId(), commandSourceStack, null);
                commandSourceStack.getReturnValueConsumer().accept(res);
                cir.setReturnValue(res);
            }
        }
    }

}
