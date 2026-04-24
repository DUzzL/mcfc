package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements FunctionCompilerClassLoader.SourceFileProvider {

    @Unique private final HashMap<DataLocation, List<String>> sources = new HashMap<>();

    // Without this, entries from disabled/removed datapacks persist and get compiled as ghost handles (and hit the content-addressed cache).
    @Inject(method = "reload(Lnet/minecraft/server/packs/resources/PreparableReloadListener$SharedState;Ljava/util/concurrent/Executor;Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    public void mcfc$clearSourcesBeforeReload(PreparableReloadListener.SharedState sharedState, Executor preparationExecutor, PreparableReloadListener.PreparationBarrier barrier, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        synchronized(sources) {
            sources.clear();
        }
    }

    @Inject(method = "lambda$reload$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;"))
    public void loadFunction(Map.Entry<Identifier, Resource> entry, Identifier resourceLocation, CommandSourceStack commandSourceStack, CallbackInfoReturnable<CommandFunction<CommandSourceStack>> cir, @Local(name = "lines") List<String> lines) {
        synchronized(sources) {
            sources.put(new DataLocation(resourceLocation.getNamespace(), resourceLocation.getPath()), lines);
        }
    }

    @Override
    public Map<DataLocation, List<String>> mcfc$getSourceFiles() {
        synchronized(sources) {
            return new HashMap<>(sources);
        }
    }
}
