package dev.epicpix.minecraftfunctioncompiler;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import org.objectweb.asm.Label;
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
                            load_getScoreboard(locals, mv);
                            load_getObjective(locals, mv, context.getArguments().get("objective").getResult().toString(), false);

                            mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;");
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "keySet", "()Ljava/util/Set;", true);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/google/common/collect/Lists", "newArrayList", "(Ljava/lang/Iterable;)Ljava/util/ArrayList;", false);
                            locals.put("$Array", locals.size());
                            mv.visitVarInsn(Opcodes.ASTORE, locals.get("$Array"));

                            mv.visitVarInsn(Opcodes.ALOAD, locals.get("$Array"));
                            locals.put("$ArrayLength", locals.size());
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                            mv.visitVarInsn(Opcodes.ISTORE, locals.get("$ArrayLength"));

                            locals.put("$ArrayIndex", locals.size());
                            pushInt(mv, 0);
                            mv.visitVarInsn(Opcodes.ISTORE, locals.get("$ArrayIndex"));
                            var beginLabel = new Label();
                            var endLabel = new Label();
                            mv.visitLabel(beginLabel);
                            mv.visitVarInsn(Opcodes.ILOAD, locals.get("$ArrayIndex"));
                            mv.visitVarInsn(Opcodes.ILOAD, locals.get("$ArrayLength"));
                            mv.visitJumpInsn(Opcodes.IF_ICMPGE, endLabel);

                            load_getScoreboard(locals, mv);
                            mv.visitVarInsn(Opcodes.ALOAD, locals.get("$Array"));
                            mv.visitVarInsn(Opcodes.ILOAD, locals.get("$ArrayIndex"));
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");

                            load_getObjective(locals, mv, context.getArguments().get("objective").getResult().toString(), true);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false);

                            mv.visitInsn(Opcodes.DUP);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "getScore", "()I", false);
                            pushInt(mv, (int)context.getArguments().get("score").getResult());
                            mv.visitInsn(Opcodes.IADD);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "setScore", "(I)V", false);

                            mv.visitIincInsn(locals.get("$ArrayIndex"), 1);
                            mv.visitJumpInsn(Opcodes.GOTO, beginLabel);
                            mv.visitLabel(endLabel);

                            locals.remove("$Array");
                            locals.remove("$ArrayIndex");
                            locals.remove("$ArrayLength");
                        }else {
                            load_getObjectiveScore(locals, mv, context.getArguments().get("objective").getResult().toString(), targetsRange.get(reader.getString()), true);

                            mv.visitInsn(Opcodes.DUP);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "getScore", "()I", false);
                            pushInt(mv, (int)context.getArguments().get("score").getResult());
                            mv.visitInsn(Opcodes.IADD);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "setScore", "(I)V", false);
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
            mv.visitVarInsn(Opcodes.ASTORE, location);
            mv.visitVarInsn(Opcodes.ALOAD, location);
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
            mv.visitVarInsn(Opcodes.ASTORE, location);
            mv.visitVarInsn(Opcodes.ALOAD, location);
        }else {
            mv.visitVarInsn(Opcodes.ALOAD, locals.get("ServerScoreboard"));
        }
    }

    private static void load_getObjective(HashMap<String, Integer> locals, MethodVisitor mv, String objective, boolean keepStack) {
        if(locals.get("Objective-" + objective) == null) {
            load_getScoreboard(locals, mv);
            mv.visitLdcInsn(objective);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;", false);
            int location = locals.size();
            locals.put("Objective-" + objective, location);
            mv.visitVarInsn(Opcodes.ASTORE, location);
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, location);
        }else {
            if(keepStack) mv.visitVarInsn(Opcodes.ALOAD, locals.get("Objective-" + objective));
        }
    }

    private static void load_getObjectiveScore(HashMap<String, Integer> locals, MethodVisitor mv, String objective, String holder, boolean keepStack) {
        if(locals.get("Objective-" + objective + ":" + holder) == null) {
            load_getScoreboard(locals, mv);
            mv.visitLdcInsn(holder);
            load_getObjective(locals, mv, objective, true);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false);

            int location = locals.size();
            locals.put("Objective-" + objective + ":" + holder, location);
            mv.visitVarInsn(Opcodes.ASTORE, location);
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, location);
        }else {
            if(keepStack) mv.visitVarInsn(Opcodes.ALOAD, locals.get("Objective-" + objective + ":" + holder));
        }
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

}
