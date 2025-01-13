package dev.epicpix.minecraftfunctioncompiler.mixin.v1_21_2;

import dev.epicpix.minecraftfunctioncompiler.v1_21_2.FunctionCompilerMain;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    public void stopServer(CallbackInfo ci) {
        FunctionCompilerMain.functionClassLoader = null;
    }

}
