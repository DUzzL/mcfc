package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.arguments.ScoreHolderArgument
import org.objectweb.asm.Opcodes

object CodeGenerator {

    fun generateCommand(command: Command, cw: CodeWriter) {
        when(command.type) {
            CommandType.KILL_SELF -> {
                cw.visitGetCommandSourceStack()
                cw.callMethod("net/minecraft/commands/CommandSourceStack", "getEntity", "()Lnet/minecraft/world/entity/Entity;")
                val uidLocal = cw.generateUniqueLocal("\$Entity")
                cw.storeLocal(uidLocal, "Lnet/minecraft/world/entity/Entity;") {
                    cw.loadLocal(uidLocal)
                    cw.executeIf(Opcodes.IFNONNULL) {
                        cw.loadLocal(uidLocal)
                        cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
                    }
                }
            }

            CommandType.KILL_SELECTOR -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], allowEntities = true, allowMultiple = true) { getter ->
                    getter()
                    cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_ADD -> {
                runScoreboardOperation(command, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.IADD)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_REMOVE -> {
                runScoreboardOperation(command, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.ISUB)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_RESET -> {
                runScoreboardOperation(command, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_SET -> {
                runScoreboardOperation(command, cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInt(command["score"])
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            else -> throw UnsupportedOperationException("Cannot compile command: ${command.type}")
        }
    }

    private fun runScoreboardOperation(command: Command, cw: CodeWriter, run: () -> Unit) {
        val targets: ScoreHolderArgument = command["targets"]

        cw.visitGetObjective(command["objective"])
        cw.executeIf(Opcodes.IFNONNULL) {
            cw.visitGetObjective(command["objective"])
            cw.callMethod("net/minecraft/world/scores/Objective", "getCriteria", "()Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;")
            cw.callMethod("net/minecraft/world/scores/criteria/ObjectiveCriteria", "isReadOnly", "()Z")
            cw.executeIf(Opcodes.IFNE) condition@ {
                visitForEachScoreHolder(cw, targets) { getter ->
                    cw.visitGetScoreboard()
                    getter()
                    cw.visitGetObjective(command["objective"])
                    run()
                }
            }
        }
    }

    private fun visitForEachScoreHolder(cw: CodeWriter, scoreHolder: ScoreHolderArgument, run: (getter: () -> Unit) -> Unit) {
        if(scoreHolder.all) {
            cw.newObject("java/util/ArrayList", "(Ljava/util/Collection;)V") {
                cw.visitGetScoreboard()
                cw.getObjectField("net/minecraft/world/scores/Scoreboard", "playerScores", "Ljava/util/Map;")
                cw.callInterfaceMethod("java/util/Map", "keySet", "()Ljava/util/Set;")
            }

            val uidLocal = cw.generateUniqueLocal("\$Array")
            cw.storeLocal(uidLocal, "Ljava/util/ArrayList;") {
                cw.createArrayListForEachLoop(uidLocal) { _, getter ->
                    run {
                        getter()
                        cw.castType("java/lang/String")
                    }
                }
            }
            return
        }

        if(scoreHolder.name != null) {
            run {
                cw.writeString(scoreHolder.name)
            }
            return
        }

        if (scoreHolder.selector != null) {
            EntitySelectorCodeGenerator.generateForEachEntity(cw, scoreHolder.selector, allowEntities = true, allowMultiple = true) { getter ->
                val uidLocal = cw.generateUniqueLocal("\$Entity")
                getter()
                cw.callFunction("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                cw.storeLocal(uidLocal, "net/minecraft/world/entity/Entity") {
                    run {
                        cw.loadLocal(uidLocal)
                    }
                }
            }
        }
    }

}
