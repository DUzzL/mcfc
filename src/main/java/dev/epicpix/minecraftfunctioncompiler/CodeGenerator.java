package dev.epicpix.minecraftfunctioncompiler;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;

public class CodeGenerator {

    public static void generateCommand(String commandName, ImmutableStringReader reader, CommandContextBuilder<CommandSourceStack> context, HashMap<String, Integer> locals, MethodVisitor mv) {
        switch(commandName) {
            case "scoreboard" -> generateCommandScoreboard(context, reader, locals, mv);
            default -> throw new UnsupportedOperationException("Cannot convert command: " + commandName);
        }
    }

    private static void generateCommandScoreboard(CommandContextBuilder<CommandSourceStack> context, ImmutableStringReader reader, HashMap<String, Integer> locals, MethodVisitor mv) {
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
                            load_getObjectiveScore(locals, mv, context.getArguments().get("objective").getResult().toString(), targetsRange.get(reader.getString()));

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


    private static void load_getServer(HashMap<String, Integer> locals, MethodVisitor mv) {
        if(locals.get("MinecraftServer") == null) {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("CommandSourceStack"));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;", false);
            int location = locals.size();
            locals.put("MinecraftServer", location);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ASTORE, location);
        }else {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("MinecraftServer"));
        }
    }

    private static void load_getScoreboard(HashMap<String, Integer> locals, MethodVisitor mv) {
        if(locals.get("ServerScoreboard") == null) {
            load_getServer(locals, mv);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;", false);
            int location = locals.size();
            locals.put("ServerScoreboard", location);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ASTORE, location);
        }else {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("ServerScoreboard"));
        }
    }

    private static void load_getObjective(HashMap<String, Integer> locals, MethodVisitor mv, String objective) {
        if(locals.get("Objective-" + objective) == null) {
            load_getScoreboard(locals, mv);
            mv.visitLdcInsn(objective);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;", false);
            int location = locals.size();
            locals.put("Objective-" + objective, location);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ASTORE, location);
        }else {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("Objective-" + objective));
        }
    }

    private static void load_getObjectiveScore(HashMap<String, Integer> locals, MethodVisitor mv, String objective, String holder) {
        if(locals.get("Objective-" + objective + ":" + holder) == null) {
            load_getScoreboard(locals, mv);
            mv.visitLdcInsn(holder);
            load_getObjective(locals, mv, objective);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false);

            int location = locals.size();
            locals.put("Objective-" + objective + ":" + holder, location);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ASTORE, location);
        }else {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("Objective-" + objective + ":" + holder));
        }
    }

}
