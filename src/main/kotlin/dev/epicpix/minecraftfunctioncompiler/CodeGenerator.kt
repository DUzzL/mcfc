package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.arguments.ScoreHolderArgument
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

object CodeGenerator {

    fun generateCommandLike(commandLike: CommandLike, cw: CodeWriter) {
        if(commandLike is Command) {
            generateCommand(commandLike, cw)
        }else if(commandLike is CommandBlock) {
            generateCommandBlock(commandLike, cw)
        }
    }

    fun generateCommandBlock(block: CommandBlock, cw: CodeWriter) {
        val skipLabel = Label()
        block.requirements.forEach {
            fulfillRequirement(it, skipLabel, cw)
            cw.updateControlFlow(1)
        }
        for(commandLike in block.commands) {
            generateCommandLike(commandLike, cw)
        }
        cw.methodVisitor.visitLabel(skipLabel)
        cw.updateControlFlow(-block.requirements.size)
    }

    fun fulfillRequirement(requirement: CommandRequirement, skipLabel: Label, cw: CodeWriter) {
        when(requirement.type) {
            CommandRequirementType.OBJECTIVE_EXISTS -> {
                cw.visitGetObjective(requirement.data["objective"] as String)
                cw.methodVisitor.visitJumpInsn(Opcodes.IFNULL, skipLabel)
            }
            CommandRequirementType.OBJECTIVE_NOT_LOCKED -> {
                cw.visitGetObjective(requirement.data["objective"] as String)
                cw.callMethod("net/minecraft/world/scores/Objective", "getCriteria", "()Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;")
                cw.callMethod("net/minecraft/world/scores/criteria/ObjectiveCriteria", "isReadOnly", "()Z")
                cw.methodVisitor.visitJumpInsn(Opcodes.IFNE, skipLabel)
            }
            CommandRequirementType.ENTITY_SELF_EXISTS -> {
                cw.visitGetSelfEntity()
                cw.methodVisitor.visitJumpInsn(Opcodes.IFNULL, skipLabel)
            }
        }
    }

    fun generateCommand(command: Command, cw: CodeWriter) {
        when(command.type) {
            CommandType.KILL -> {
                EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], allowEntities = true, allowMultiple = true) { getter ->
                    getter()
                    cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_ADD -> {
                runScoreboardOperation(command["targets"], command["objective"], cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.IADD)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_REMOVE -> {
                runScoreboardOperation(command["targets"], command["objective"], cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInstruction(Opcodes.DUP)
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.writeInt(command["score"])
                    cw.writeInstruction(Opcodes.ISUB)
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_RESET -> {
                runScoreboardOperation(command["targets"], command["objective"], cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_SET -> {
                runScoreboardOperation(command["targets"], command["objective"], cw) {
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.writeInt(command["score"])
                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                }
            }

            CommandType.SCOREBOARD_PLAYERS_OPERATION -> {
                visitForEachScoreHolder(cw, command["targets"]) { targetGetter ->
                    visitForEachScoreHolder(cw, command["source"]) { sourceGetter ->
                        fun source() {
                            cw.visitGetScoreboard()
                            sourceGetter()
                            cw.visitGetObjective(command["sourceObjective"])
                            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        }
                        fun target() {
                            cw.visitGetScoreboard()
                            targetGetter()
                            cw.visitGetObjective(command["targetObjective"])
                            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                        }
                        when(command.get<String>("operation")) {
                            "=" -> {
                                target()
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "+=" -> {
                                target()
                                cw.writeInstruction(Opcodes.DUP)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInstruction(Opcodes.IADD)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "-=" -> {
                                target()
                                cw.writeInstruction(Opcodes.DUP)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInstruction(Opcodes.ISUB)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            else -> throw UnsupportedOperationException("Unknown operation operator: ${command.get<String>("operation")}")
                        }
                    }
                }
            }

            else -> throw UnsupportedOperationException("Cannot compile command: ${command.type}: ${command.params}")
        }
    }

    private fun runScoreboardOperation(targets: ScoreHolderArgument, objective: String, cw: CodeWriter, run: () -> Unit) {
        visitForEachScoreHolder(cw, targets) { getter ->
            cw.visitGetScoreboard()
            getter()
            cw.visitGetObjective(objective)
            run()
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

        if(scoreHolder.selector != null) {
            EntitySelectorCodeGenerator.generateForEachEntity(cw, scoreHolder.selector, allowEntities = true, allowMultiple = true) { getter ->
                val uidLocal = cw.generateUniqueLocal("\$EntityName")
                getter()
                cw.callMethod("net/minecraft/world/entity/Entity", "getScoreboardName", "()Ljava/lang/String;")
                cw.storeLocal(uidLocal, "Ljava/lang/String;") {
                    run {
                        cw.loadLocal(uidLocal)
                    }
                }
            }
        }
    }

}
