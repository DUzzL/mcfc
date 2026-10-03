package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionSourceCapture;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements FunctionSourceCapture {

    // Written by vanilla's parallel function-loading tasks.
    @Unique private final Map<Identifier, List<String>> mcfc$sources = new ConcurrentHashMap<>();

    // Records the lines only once vanilla parsed them, so functions vanilla rejected are never compiled.
    @WrapOperation(method = "lambda$reload$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;"))
    private <T extends ExecutionCommandSource<T>> CommandFunction<T> mcfc$captureLines(Identifier id, CommandDispatcher<T> dispatcher, T compilationContext, List<String> lines, Operation<CommandFunction<T>> original) {
        var function = original.call(id, dispatcher, compilationContext, lines);
        mcfc$sources.put(id, lines);
        return function;
    }

    @Override
    public Map<Identifier, List<String>> mcfc$capturedSources() {
        return mcfc$sources;
    }
}
