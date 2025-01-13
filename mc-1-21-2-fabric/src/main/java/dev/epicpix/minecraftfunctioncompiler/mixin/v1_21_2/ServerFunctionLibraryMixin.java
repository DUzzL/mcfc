package dev.epicpix.minecraftfunctioncompiler.mixin.v1_21_2;

import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements FunctionCompilerClassLoader.SourceFileProvider {

    @Unique private final HashMap<DataLocation, List<String>> sources = new HashMap<>();

    @Inject(method = "method_29451", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    public void loadFunction(Map.Entry<ResourceLocation, Resource> entry, ResourceLocation resourceLocation, CommandSourceStack commandSourceStack, CallbackInfoReturnable<CommandFunction<CommandSourceStack>> cir, List<String> lines) {
        synchronized(sources) {
            sources.put(new DataLocation(resourceLocation.getNamespace(), resourceLocation.getPath()), lines);
        }
    }

    public Map<DataLocation, List<String>> mcfc$getSourceFiles() {
        return new HashMap<>(sources);
    }
}
