package dev.epicpix.minecraftfunctioncompiler.v1_21;

import com.mojang.brigadier.CommandDispatcher;
import dev.epicpix.minecraftfunctioncompiler.CompileConstants;
import dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

import static net.minecraft.server.commands.FunctionCommand.SUGGEST_FUNCTION;

public class McfcCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("mcfc").requires(stack -> stack.hasPermission(3));
        base.then(Commands.literal("on").executes(context -> {
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
        }));
        base.then(Commands.literal("off").executes(context -> {
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
        }));
        base.then(Commands.literal("state").executes(context -> {
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
        }));
        base.then(Commands.literal("show_diagnostics").executes(context -> {
            var loader = FunctionCompilerMain.functionClassLoader;
            if(loader == null) {
                context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                return 0;
            }
            var diagnostics = loader.getDiagnostics();
            context.getSource().sendSuccess(() -> Component.literal("Total diagnostics: ").withStyle(ChatFormatting.GREEN).append(Component.literal(CompileConstants.versionName).withStyle(ChatFormatting.GOLD)), false);
            for(var diagnostic : diagnostics) {
                var tooltip = Component.empty();
                tooltip.append(Component.literal("Id: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.diagnosticId()).withStyle(ChatFormatting.GOLD));
                tooltip.append(Component.literal("\nKind: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.kind().toString().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
                tooltip.append(Component.literal("\nSeverity: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.severity().toString().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
                tooltip.append(Component.literal("\nSource: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.source().toString()).withStyle(ChatFormatting.GOLD));
                tooltip.append(Component.literal("\nMessage: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.message()).withStyle(ChatFormatting.GOLD));
                tooltip.append(Component.literal("\nExtra: ").withStyle(ChatFormatting.GREEN));
                tooltip.append(Component.literal(diagnostic.extra()).withStyle(ChatFormatting.GOLD));
                context.getSource().sendSuccess(() -> Component.literal(diagnostic.kind() + " - " + diagnostic.source()).withStyle(Style.EMPTY.applyFormat(ChatFormatting.GREEN).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))), false);
            }
            return diagnostics.size();
        }));
        base.then(Commands.literal("is_compiled").then(Commands.argument("function", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(context -> {
            var loader = FunctionCompilerMain.functionClassLoader;
            if(loader == null) {
                context.getSource().sendFailure(Component.literal("Function Compiler is not loaded"));
                return 0;
            }
            int res = 0;
            for(var func : FunctionArgument.getFunctions(context, "function")) {
                boolean isCompiled = loader.hasFunctionLoaded(func.id());
                context.getSource().sendSuccess(() -> Component.literal("Function ").withStyle(ChatFormatting.AQUA).append(Component.literal(func.id().toString()).withStyle(ChatFormatting.GOLD)).append(Component.literal(" is ").withStyle(ChatFormatting.AQUA).append(isCompiled ? Component.literal("compiled").withStyle(ChatFormatting.GREEN) : Component.literal("not compiled").withStyle(ChatFormatting.RED))), false);
                if(isCompiled) res++;
            }
            return res;
        })));
        dispatcher.register(base);
    }
}
