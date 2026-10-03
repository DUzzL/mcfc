package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerRecursionGuard;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    public void stopServer(CallbackInfo ci) {
        // Abandon any running compile and drop every static reference to this server's loader.
        FunctionCompilerClassLoader.cancelAllCompiles();
        FunctionCompilerMain.functionClassLoader = null;
        FunctionCompilerRecursionGuard.depth = 0;
        // Runtime parse caches hold this server's registry holders.
        dev.epicpix.minecraftfunctioncompiler.mod.ExtensionFunctions.onServerStopped();
    }

}
