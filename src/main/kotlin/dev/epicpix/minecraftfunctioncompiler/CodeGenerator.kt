package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.context.CommandContextBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ScoreHolderArgument
import org.objectweb.asm.Opcodes

object CodeGenerator {
    fun generateCommand(commandName: String, reader: ImmutableStringReader, context: CommandContextBuilder<CommandSourceStack>, cw: CodeWriter) {
        when (commandName) {
            "scoreboard" -> generateCommandScoreboard(context, reader, cw)
            else -> throw UnsupportedOperationException("Cannot convert command: $commandName")
        }
    }

    private fun generateCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, reader: ImmutableStringReader, cw: CodeWriter) {
        val type = context.nodes[1].node.name
        if (type == "players") {
            val opType = context.nodes[2].node.name
            when (opType) {
                "add" -> {
                    runScoreboardOperation(context, reader, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInstruction(Opcodes.DUP)
                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        cw.writeInt(context.arguments["score"]!!.result as Int)
                        cw.writeInstruction(Opcodes.IADD)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "enable" -> throw UnsupportedOperationException("Unsupported operation: enable")
                "operation" -> throw UnsupportedOperationException("Unsupported operation: operation")
                "remove" -> {
                    runScoreboardOperation(context, reader, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInstruction(Opcodes.DUP)
                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        cw.writeInt(context.arguments["score"]!!.result as Int)
                        cw.writeInstruction(Opcodes.ISUB)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "reset" -> {
                    runScoreboardOperation(context, reader, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
                    }
                }
                "set" -> {
                    runScoreboardOperation(context, reader, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInt(context.arguments["score"]!!.result as Int)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "get", "list" -> {}
                else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: " + context.nodes)
            }
        } else if (type == "objectives") {
            throw UnsupportedOperationException("Unsupported scoreboard objectives arguments: " + context.nodes)
        } else {
            throw UnsupportedOperationException("Unsupported scoreboard operation type: $type")
        }
    }

    private fun runScoreboardOperation(context: CommandContextBuilder<CommandSourceStack>, reader: ImmutableStringReader, cw: CodeWriter, run: () -> Unit) {
        val targets = context.arguments["targets"]!!.result
        if (targets is ScoreHolderArgument.SelectorResult) {
            cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
            cw.executeIf(Opcodes.IFNONNULL) {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, targets.selector, context.arguments["targets"]!!.range.get(reader)) {
                    cw.callFunction("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                    cw.visitGetScoreboard()
                    cw.writeInstruction(Opcodes.SWAP)
                    cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
                    run()
                }
            }
        } else {
            val targetsRange = context.arguments["targets"]!!.range
            if (targetsRange.length == 1 && reader.string[targetsRange.start] == '*') {
                cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
                cw.executeIf(Opcodes.IFNONNULL) {
                    cw.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                        cw.visitGetScoreboard()
                        cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                        cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
                    }

                    cw.createStackArrayListForEachLoop { getter ->
                        cw.visitGetScoreboard()
                        getter()
                        cw.castType("java/lang/String")
                        cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
                        run()
                    }
                }
            } else {
                cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
                cw.executeIf(Opcodes.IFNONNULL) {
                    cw.visitGetScoreboard()
                    cw.writeString(targetsRange[reader.string])
                    cw.visitGetObjective(context.arguments["objective"]!!.result.toString())
                    run()
                }
            }
        }
    }

}
