package dev.epicpix.minecraftfunctioncompiler.mixin;

import dev.epicpix.minecraftfunctioncompiler.FunctionClassLoader;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin {

    @Shadow private ServerFunctionLibrary library;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        FunctionClassLoader classLoader = new FunctionClassLoader(getClass().getClassLoader(), library.getFunctions());
        try {
            var clz = classLoader.loadClass("test.abc");
            System.out.println(clz);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}
