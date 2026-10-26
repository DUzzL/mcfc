package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilationStarter;
import dev.epicpix.minecraftfunctioncompiler.mod.CompiledDispatchCache;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionClassLoader;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerRecursionGuard;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionDebugLogger;
import dev.epicpix.minecraftfunctioncompiler.mod.ServerThreadGate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements FunctionCompilationStarter {

    @Shadow @Final public MinecraftServer server;

    // Server thread. Never blocks (outside blocking/test mode): the compile runs on the compiler thread and
    // publishes itself back on the server thread; a newer reload supersedes it.
    @Inject(method = "postReload", at = @At("TAIL"))
    public void postReload(ServerFunctionLibrary serverFunctionLibrary, CallbackInfo ci) {
        mcfc$loadFunctions(true, () -> {});
    }

    @Override
    public void mcfc$loadFunctions(boolean autoEnable, Runnable onComplete) {
        var manager = (ServerFunctionManager) (Object) this;
        var loader = new FunctionClassLoader(getClass().getClassLoader(), server, manager);
        FunctionCompilerMain.functionClassLoader = loader;
        // /mcfc recompile keeps the library: drop slots cached under the previous loader so they don't pin it.
        for(var function : manager.library.getFunctions().values()) {
            if(function instanceof CompiledDispatchCache cache) cache.mcfc$resetDispatchCache();
        }
        loader.loadFunctions(autoEnable, onComplete);
    }

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    public void run(CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CallbackInfo ci) {
        // Compiled bodies use server-thread-only state; any other thread runs vanilla's interpreter.
        if(!ServerThreadGate.onServerThread()) return;
        // Inside a running vanilla command (e.g. an advancement reward during `advancement grant`) vanilla queues
        // the call after that command (Commands.executeCommandInContext); CallFunctionMixin then runs it compiled.
        if(Commands.CURRENT_EXECUTION_CONTEXT.get() != null) return;
        var loader = FunctionCompilerMain.functionClassLoader;
        // Only plain functions: a macro function called without arguments fails to instantiate in vanilla.
        if(loader != null && commandFunction instanceof CompiledDispatchCache cache && loader.isUseReady()) {
            var entry = cache.mcfc$entry(loader);
            var compiled = entry.function;
            if(compiled != null && !FunctionCompilerRecursionGuard.atLimit()) {
                if(FunctionDebugLogger.enabled) FunctionDebugLogger.recordCompiledCall(commandFunction.id());
                loader.invoke(entry, compiled, commandSourceStack, null);
                ci.cancel();
                return;
            }
        }
        if(FunctionDebugLogger.enabled) FunctionDebugLogger.recordCall(commandFunction.id(), true);
    }

}
