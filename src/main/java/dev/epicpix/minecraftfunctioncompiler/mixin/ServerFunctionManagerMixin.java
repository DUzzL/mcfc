package dev.epicpix.minecraftfunctioncompiler.mixin;

import dev.epicpix.minecraftfunctioncompiler.FunctionClassLoader;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin {

    @Shadow private ServerFunctionLibrary library;
    @Unique private FunctionClassLoader classLoader;
    @Shadow private MinecraftServer server;

    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        classLoader = new FunctionClassLoader(getClass().getClassLoader(), library.getFunctions(), server);
        classLoader.loadFunctions((ServerFunctionManager) (Object) this);
    }

    @Inject(method = "execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction commandFunction, CommandSourceStack commandSourceStack, ServerFunctionManager.TraceCallbacks traceCallbacks, CompoundTag compoundTag, CallbackInfoReturnable<Integer> cir) {
        if(classLoader != null) {
            if(classLoader.hasFunctionLoaded(commandFunction.getId())) {
                int res = classLoader.runFunction(commandFunction.getId(), commandSourceStack);
                cir.setReturnValue(res);
            }
        }
    }

}
