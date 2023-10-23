package dev.epicpix.minecraftfunctioncompiler.mixin;

import dev.epicpix.minecraftfunctioncompiler.FunctionClassLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin {

    @Shadow private ServerFunctionLibrary library;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        FunctionClassLoader classLoader = new FunctionClassLoader(getClass().getClassLoader(), library.getFunctions());
        try {
            var clz = classLoader.loadClass("test.abc");
            var handle = MethodHandles.lookup().findStatic(clz, "run", MethodType.methodType(void.class, CommandSourceStack.class));
            handle.invoke((Object) null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}
