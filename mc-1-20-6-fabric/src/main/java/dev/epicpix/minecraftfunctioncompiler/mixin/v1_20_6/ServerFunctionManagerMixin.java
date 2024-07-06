package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_6;

import dev.epicpix.minecraftfunctioncompiler.v1_20_6.FunctionClassLoader;
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

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.FUNCTION_RUN_MIXIN_ENABLED;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin {

    @Shadow public ServerFunctionLibrary library;
    @Unique private FunctionClassLoader classLoader;
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), library.getFunctions(), server);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this);
    }

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CallbackInfo ci) {
        if(FUNCTION_RUN_MIXIN_ENABLED && classLoader != null) {
            if(classLoader.hasFunctionLoaded(commandFunction.id())) {
                classLoader.runFunction(commandFunction.id(), commandSourceStack);
                ci.cancel();
            }
        }
    }

}
