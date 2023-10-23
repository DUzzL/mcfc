package dev.epicpix.minecraftfunctioncompiler;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CodeGenerator {

    public static void generateCommand(String commandName, ImmutableStringReader reader, CommandContextBuilder<CommandSourceStack> context, MethodVisitor mv) {
        switch(commandName) {
            case "scoreboard" -> generateCommandScoreboard(context, reader, mv);
            default -> throw new UnsupportedOperationException("Cannot convert command: " + commandName);
        }
    }

    private static void generateCommandScoreboard(CommandContextBuilder<CommandSourceStack> context, ImmutableStringReader reader, MethodVisitor mv) {
        var type = context.getNodes().get(1).getNode().getName();
        if(type.equals("players")) {
            String opType = context.getNodes().get(2).getNode().getName();
            switch(opType) {
                case "add" -> {
                    var targets = context.getArguments().get("targets").getResult();
                    if(targets instanceof ScoreHolderArgument.SelectorResult) {
                        System.out.println("Selector");
                    }else {
                        var targetsRange = context.getArguments().get("targets").getRange();
                        if(targetsRange.getLength() == 1 && reader.getString().charAt(targetsRange.getStart()) == '*') {
                            System.out.println("All");
                        }else {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;", false);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;", false);
                            mv.visitInsn(Opcodes.DUP);

                            mv.visitLdcInsn(context.getArguments().get("objective").getResult().toString());
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;", false);

                            mv.visitLdcInsn(targetsRange.get(reader.getString()));
                            mv.visitInsn(Opcodes.SWAP);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false);
                            mv.visitInsn(Opcodes.DUP);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "getScore", "()I", false);
                            mv.visitLdcInsn(context.getArguments().get("score").getResult());
                            mv.visitInsn(Opcodes.IADD);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "setScore", "(I)V", false);

                            System.out.println("Specified");
                        }
                    }
                }
                case "enable" -> {

                }
                case "operation" -> {

                }
                case "remove" -> {

                }
                case "reset" -> {

                }
                case "set" -> {

                }
                case "get", "list" -> {}
                default -> throw new UnsupportedOperationException("Unsupported scoreboard players type arguments: " + context.getNodes());
            }
        }else if(type.equals("objectives")) {
            throw new UnsupportedOperationException("Unsupported scoreboard objectives arguments: " + context.getNodes());
        }else {
            throw new UnsupportedOperationException("Unsupported scoreboard operation type: " + type);
        }
    }

    private static void findEntities() {

    }

}
