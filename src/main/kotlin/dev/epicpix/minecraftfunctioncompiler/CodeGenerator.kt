package dev.epicpix.minecraftfunctioncompiler

import net.minecraft.commands.arguments.ScoreHolderArgument
import org.objectweb.asm.Opcodes

object CodeGenerator {
    fun generateCommand(nodeStorage: BrigadierNodeStorage, cw: CodeWriter) {
        val commandName = nodeStorage[0].name
        when(commandName) {
            "kill" -> generateCommandKill(nodeStorage, cw)
            "scoreboard" -> generateCommandScoreboard(nodeStorage, cw)
            else -> throw UnsupportedOperationException("Cannot convert command: $commandName")
        }
    }

    private fun generateCommandKill(nodeStorage: BrigadierNodeStorage, cw: CodeWriter) {
        val targets = nodeStorage["targets"]
        if(targets != null) {
            EntitySelectorCodeGenerator.generateForEachEntity(cw, targets, allowEntities = true, allowMultiple = true) {
                cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
            }
        }else {
            cw.visitGetCommandSourceStack()
            cw.callMethod("net/minecraft/commands/CommandSourceStack", "getEntity", "()Lnet/minecraft/world/entity/Entity;")
            cw.defineLocal("\$Entity", "Lnet/minecraft/world/entity/Entity;") {
                cw.storeLocal("\$Entity")
                cw.loadLocal("\$Entity")
                cw.executeIf(Opcodes.IFNONNULL) {
                    cw.loadLocal("\$Entity")
                    cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
                }
            }
        }
    }

    private fun generateCommandScoreboard(nodeStorage: BrigadierNodeStorage, cw: CodeWriter) {
        val type = nodeStorage[1].name
        if (type == "players") {
            val opType = nodeStorage[2].name
            when (opType) {
                "add" -> {
                    runScoreboardOperation(nodeStorage, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInstruction(Opcodes.DUP)
                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        cw.writeInt(nodeStorage["score"]!!.data as Int)
                        cw.writeInstruction(Opcodes.IADD)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "enable" -> throw UnsupportedOperationException("Unsupported operation: enable")
                "operation" -> throw UnsupportedOperationException("Unsupported operation: operation")
                "remove" -> {
                    runScoreboardOperation(nodeStorage, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInstruction(Opcodes.DUP)
                        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                        cw.writeInt(nodeStorage["score"]!!.data as Int)
                        cw.writeInstruction(Opcodes.ISUB)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "reset" -> {
                    runScoreboardOperation(nodeStorage, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
                    }
                }
                "set" -> {
                    runScoreboardOperation(nodeStorage, cw) {
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        cw.writeInt(nodeStorage["score"]!!.data as Int)
                        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                    }
                }
                "get", "list" -> {}
                else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: $nodeStorage")
            }
        } else if (type == "objectives") {
            throw UnsupportedOperationException("Unsupported scoreboard objectives arguments: $nodeStorage")
        } else {
            throw UnsupportedOperationException("Unsupported scoreboard operation type: $type")
        }
    }

    private fun runScoreboardOperation(nodeStorage: BrigadierNodeStorage, cw: CodeWriter, run: () -> Unit) {
        val targets = nodeStorage["targets"]!!.data
        if (targets is ScoreHolderArgument.SelectorResult) {
            cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
            cw.executeIf(Opcodes.IFNONNULL) {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, nodeStorage["targets"]!!, allowEntities = true, allowMultiple = true) {
                    cw.callFunction("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                    cw.visitGetScoreboard()
                    cw.writeInstruction(Opcodes.SWAP)
                    cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
                    run()
                }
            }
        } else {
            val targetsRange = nodeStorage["targets"]!!.range
            if (targetsRange.length == 1 && nodeStorage["targets"]!!.source.string[targetsRange.start] == '*') {
                cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
                cw.executeIf(Opcodes.IFNONNULL) {
                    cw.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                        cw.visitGetScoreboard()
                        cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                        cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
                    }

                    cw.defineLocal("\$Array", "Ljava/util/ArrayList;") {
                        cw.storeLocal("\$Array")
                        cw.createArrayListForEachLoop("\$Array") { _, getter ->
                            cw.visitGetScoreboard()
                            getter()
                            cw.castType("java/lang/String")
                            cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
                            run()
                        }
                    }
                }
            } else {
                cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
                cw.executeIf(Opcodes.IFNONNULL) {
                    cw.visitGetScoreboard()
                    cw.writeString(targetsRange[nodeStorage["targets"]!!.source])
                    cw.visitGetObjective(nodeStorage["objective"]!!.data.toString())
                    run()
                }
            }
        }
    }

}
