package dev.epicpix.minecraftfunctioncompiler.mixin.v26_1;

import com.llamalad7.mixinextras.sugar.Local;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements FunctionCompilerClassLoader.SourceFileProvider {

    @Unique private final HashMap<DataLocation, List<String>> sources = new HashMap<>();

    @Inject(method = "lambda$reload$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;"))
    public void loadFunction(Map.Entry<Identifier, Resource> entry, Identifier resourceLocation, CommandSourceStack commandSourceStack, CallbackInfoReturnable<CommandFunction<CommandSourceStack>> cir, @Local(name = "lines") List<String> lines) {
        synchronized(sources) {
            sources.put(new DataLocation(resourceLocation.getNamespace(), resourceLocation.getPath()), lines);
        }
    }

    @Override
    public Map<DataLocation, List<String>> mcfc$getSourceFiles() {
        return new HashMap<>(sources);
    }
}
