package dev.epicpix.minecraftfunctioncompiler;

import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.objectweb.asm.MethodVisitor;

public class CodeGenerator {

    public static void generateCommand(String commandName, CommandContextBuilder<CommandSourceStack> context, MethodVisitor mv) {
        throw new UnsupportedOperationException("Cannot convert command: " + commandName);
    }

}
