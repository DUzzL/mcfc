package dev.epicpix.minecraftfunctioncompiler.v1_20_1;

import com.mojang.brigadier.CommandDispatcher;
import dev.epicpix.minecraftfunctioncompiler.CompileConstants;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.network.chat.Component;

import static net.minecraft.server.commands.FunctionCommand.SUGGEST_FUNCTION;

public class McfcCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcfc").requires(stack -> stack.hasPermission(3))
                .then(Commands.literal("on").executes(context -> {
                    var loader = FunctionCompilerMain.functionClassLoader;
                    if(loader == null) {
                        context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                        return 0;
                    }
                    if(loader.getState() == FunctionCompilerClassLoader.State.READY_OFF || loader.getState() == FunctionCompilerClassLoader.State.READY_ON || loader.getState() == FunctionCompilerClassLoader.State.EXECUTION_FAILED) {
                        loader.tryOn();
                        context.getSource().sendSuccess(() -> Component.literal("Turned on Function Compiler").withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    }else {
                        return 0;
                    }
                }))
                .then(Commands.literal("off").executes(context -> {
                    var loader = FunctionCompilerMain.functionClassLoader;
                    if(loader == null) {
                        context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                        return 0;
                    }
                    if(loader.getState() == FunctionCompilerClassLoader.State.READY_OFF || loader.getState() == FunctionCompilerClassLoader.State.READY_ON || loader.getState() == FunctionCompilerClassLoader.State.EXECUTION_FAILED) {
                        loader.tryOff();
                        context.getSource().sendSuccess(() -> Component.literal("Turned off Function Compiler").withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    }else {
                        return 0;
                    }
                }))
                .then(Commands.literal("state").executes(context -> {
                    var loader = FunctionCompilerMain.functionClassLoader;
                    if(loader == null) {
                        context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Function Compiler Version: ").withStyle(ChatFormatting.GREEN).append(Component.literal(CompileConstants.versionName).withStyle(ChatFormatting.GOLD)), false);
                    context.getSource().sendSuccess(() -> Component.literal("Current Function Compiler State: ").withStyle(ChatFormatting.GREEN).append(Component.literal(loader.getState().toString()).withStyle(ChatFormatting.GOLD)), false);
                    context.getSource().sendSuccess(() -> Component.literal("Compiled Function Count: ").withStyle(ChatFormatting.GREEN).append(Component.literal(Integer.toString(loader.loadedFunctionCount())).withStyle(ChatFormatting.GOLD)), false);
                    context.getSource().sendSuccess(() -> Component.literal("Total Function Count: ").withStyle(ChatFormatting.GREEN).append(Component.literal(Integer.toString(loader.totalFunctionCount())).withStyle(ChatFormatting.GOLD)), false);
                    return loader.getState().ordinal();
                }))
                .then(Commands.literal("is_compiled").then(Commands.argument("function", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(context -> {
                    var loader = FunctionCompilerMain.functionClassLoader;
                    if(loader == null) {
                        context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                        return 0;
                    }
                    int res = 0;
                    for(var func : FunctionArgument.getFunctions(context, "function")) {
                        boolean isCompiled = loader.hasFunctionLoaded(func.getId());
                        context.getSource().sendSuccess(() -> Component.literal("Function ").withStyle(ChatFormatting.AQUA).append(Component.literal(func.getId().toString()).withStyle(ChatFormatting.GOLD)).append(Component.literal(" is ").withStyle(ChatFormatting.AQUA).append(isCompiled ? Component.literal("compiled").withStyle(ChatFormatting.GREEN) : Component.literal("not compiled").withStyle(ChatFormatting.RED))), false);
                        if(isCompiled) res++;
                    }
                    return res;
                })))
        );
    }
}
