package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_4;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.FunctionCompilerMain;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
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

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.AUTO_SWITCH_FUNCTIONS;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements FunctionCompilationStarter {

    @Shadow public ServerFunctionLibrary library;
    @Unique private FunctionClassLoader classLoader;
    @Shadow @Final public MinecraftServer server;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        mcfc$loadFunctions(AUTO_SWITCH_FUNCTIONS, () -> {});
    }

    @Override
    public void mcfc$loadFunctions(boolean autoEnable, Runnable onComplete) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), server, (FunctionCompilerClassLoader.SourceFileProvider) library);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this, autoEnable, onComplete);
        FunctionCompilerMain.functionClassLoader = classLoader;
    }

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CallbackInfo ci) {
        if(classLoader != null && classLoader.isUseReady()) {
            if(classLoader.hasFunctionLoaded(commandFunction.id())) {
                classLoader.runFunction(commandFunction.id(), commandSourceStack, null);
                ci.cancel();
            }
        }
    }

}
