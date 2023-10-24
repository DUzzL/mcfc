package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.context.CommandContextBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ScoreHolderArgument
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

object CodeGenerator {
    fun generateCommand(commandName: String, reader: ImmutableStringReader, context: CommandContextBuilder<CommandSourceStack>, locals: HashMap<String, Int?>, mv: MethodVisitor) {
        when (commandName) {
            "scoreboard" -> generateCommandScoreboard(context, reader, locals, mv)
            else -> throw UnsupportedOperationException("Cannot convert command: $commandName")
        }
    }

    private fun generateCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, reader: ImmutableStringReader, locals: HashMap<String, Int?>, mv: MethodVisitor) {
        val type = context.nodes[1].node.name
        if (type == "players") {
            val opType = context.nodes[2].node.name
            when (opType) {
                "add" -> {
                    val targets = context.arguments["targets"]!!.result
                    if (targets is ScoreHolderArgument.SelectorResult) {
                        println("Selector")
                    } else {
                        val targetsRange = context.arguments["targets"]!!.range
                        if (targetsRange.length == 1 && reader.string[targetsRange.start] == '*') {
                            load_getScoreboard(locals, mv)
                            load_getObjective(locals, mv, context.arguments["objective"]!!.result.toString(), false)
                            mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "keySet", "()Ljava/util/Set;", true)
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/google/common/collect/Lists", "newArrayList", "(Ljava/lang/Iterable;)Ljava/util/ArrayList;", false)
                            locals["\$Array"] = locals.size
                            mv.visitVarInsn(Opcodes.ASTORE, locals["\$Array"]!!)
                            mv.visitVarInsn(Opcodes.ALOAD, locals["\$Array"]!!)
                            locals["\$ArrayLength"] = locals.size
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false)
                            mv.visitVarInsn(Opcodes.ISTORE, locals["\$ArrayLength"]!!)
                            locals["\$ArrayIndex"] = locals.size
                            pushInt(mv, 0)
                            mv.visitVarInsn(Opcodes.ISTORE, locals["\$ArrayIndex"]!!)
                            val beginLabel = Label()
                            val endLabel = Label()
                            mv.visitLabel(beginLabel)
                            mv.visitVarInsn(Opcodes.ILOAD, locals["\$ArrayIndex"]!!)
                            mv.visitVarInsn(Opcodes.ILOAD, locals["\$ArrayLength"]!!)
                            mv.visitJumpInsn(Opcodes.IF_ICMPGE, endLabel)
                            load_getScoreboard(locals, mv)
                            mv.visitVarInsn(Opcodes.ALOAD, locals["\$Array"]!!)
                            mv.visitVarInsn(Opcodes.ILOAD, locals["\$ArrayIndex"]!!)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false)
                            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")
                            load_getObjective(locals, mv, context.arguments["objective"]!!.result.toString(), true)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false)
                            mv.visitInsn(Opcodes.DUP)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "getScore", "()I", false)
                            pushInt(mv, context.arguments["score"]!!.result as Int)
                            mv.visitInsn(Opcodes.IADD)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "setScore", "(I)V", false)
                            mv.visitIincInsn(locals["\$ArrayIndex"]!!, 1)
                            mv.visitJumpInsn(Opcodes.GOTO, beginLabel)
                            mv.visitLabel(endLabel)
                            locals.remove("\$Array")
                            locals.remove("\$ArrayIndex")
                            locals.remove("\$ArrayLength")
                        } else {
                            load_getObjectiveScore(locals, mv, context.arguments["objective"]!!.result.toString(), targetsRange[reader.string], true)
                            mv.visitInsn(Opcodes.DUP)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "getScore", "()I", false)
                            pushInt(mv, context.arguments["score"]!!.result as Int)
                            mv.visitInsn(Opcodes.IADD)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Score", "setScore", "(I)V", false)
                        }
                    }
                }

                "enable" -> {}
                "operation" -> {}
                "remove" -> {}
                "reset" -> {}
                "set" -> {}
                "get", "list" -> {}
                else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: " + context.nodes)
            }
        } else if (type == "objectives") {
            throw UnsupportedOperationException("Unsupported scoreboard objectives arguments: " + context.nodes)
        } else {
            throw UnsupportedOperationException("Unsupported scoreboard operation type: $type")
        }
    }

    private fun findEntities() {}
    private fun load_getServer(locals: HashMap<String, Int?>, mv: MethodVisitor) {
        if (locals["MinecraftServer"] == null) {
            mv.visitVarInsn(Opcodes.ALOAD, locals["CommandSourceStack"]!!)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/commands/CommandSourceStack", "getServer", "()Lnet/minecraft/server/MinecraftServer;", false)
            val location = locals.size
            locals["MinecraftServer"] = location
            mv.visitVarInsn(Opcodes.ASTORE, location)
            mv.visitVarInsn(Opcodes.ALOAD, location)
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, locals["MinecraftServer"]!!)
        }
    }

    private fun load_getScoreboard(locals: HashMap<String, Int?>, mv: MethodVisitor) {
        if (locals["ServerScoreboard"] == null) {
            load_getServer(locals, mv)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;", false)
            val location = locals.size
            locals["ServerScoreboard"] = location
            mv.visitVarInsn(Opcodes.ASTORE, location)
            mv.visitVarInsn(Opcodes.ALOAD, location)
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, locals["ServerScoreboard"]!!)
        }
    }

    private fun load_getObjective(locals: HashMap<String, Int?>, mv: MethodVisitor, objective: String, keepStack: Boolean) {
        if (locals["Objective-$objective"] == null) {
            load_getScoreboard(locals, mv)
            mv.visitLdcInsn(objective)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;", false)
            val location = locals.size
            locals["Objective-$objective"] = location
            mv.visitVarInsn(Opcodes.ASTORE, location)
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, location)
        } else {
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, locals["Objective-$objective"]!!)
        }
    }

    private fun load_getObjectiveScore(locals: HashMap<String, Int?>, mv: MethodVisitor, objective: String, holder: String, keepStack: Boolean) {
        if (locals["Objective-$objective:$holder"] == null) {
            load_getScoreboard(locals, mv)
            mv.visitLdcInsn(holder)
            load_getObjective(locals, mv, objective, true)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;", false)
            val location = locals.size
            locals["Objective-$objective:$holder"] = location
            mv.visitVarInsn(Opcodes.ASTORE, location)
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, location)
        } else {
            if (keepStack) mv.visitVarInsn(Opcodes.ALOAD, locals["Objective-$objective:$holder"]!!)
        }
    }

    private fun pushInt(mv: MethodVisitor, value: Int) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value)
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value)
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value)
        } else {
            mv.visitLdcInsn(value)
        }
    }
}
