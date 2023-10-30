package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.*
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun generate_scoreboard_players_add(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    runScoreboardOperation(command["targets"], command["objective"], context, cw) {
        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
        cw.writeInstruction(Opcodes.DUP)
        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
        cw.writeInt(command["score"])
        cw.writeInstruction(Opcodes.IADD)
        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
    }
}

fun generate_scoreboard_players_operation(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    context.runWithObjective(cw, command["sourceObjective"]) { sourceObjective, context ->
        context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
            val skip = Label()
            visitForEachScoreHolder(cw, context, command["targets"]) { targetGetter ->
                visitForEachScoreHolder(cw, context, command["source"]) { sourceGetter ->
                    fun source() {
                        cw.visitGetScoreboard()
                        sourceGetter()
                        cw.loadLocal(sourceObjective)
                        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    }
                    cw.visitGetScoreboard()
                    targetGetter()
                    cw.loadLocal(targetObjective)
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    val targetLocal = cw.generateUniqueLocal("\$ScoreTarget")
                    cw.storeLocal(targetLocal, "Lnet/minecraft/world/scores/Score;") {
                        when(command.get<String>("operation")) {
                            "=" -> {
                                cw.loadLocal(targetLocal)
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "+=" -> {
                                cw.loadLocal(targetLocal)
                                cw.loadLocal(targetLocal)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInstruction(Opcodes.IADD)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "-=" -> {
                                cw.loadLocal(targetLocal)
                                cw.loadLocal(targetLocal)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInstruction(Opcodes.ISUB)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "*=" -> {
                                cw.loadLocal(targetLocal)
                                cw.loadLocal(targetLocal)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.writeInstruction(Opcodes.IMUL)
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "/=" -> {
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                val uidLocal = cw.generateUniqueLocal("\$Score")
                                cw.storeLocal(uidLocal, "I") {
                                    loadLocal(uidLocal)
                                    cw.methodVisitor.visitJumpInsn(Opcodes.IFEQ, skip)
                                    cw.loadLocal(targetLocal)
                                    cw.loadLocal(targetLocal)
                                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                    loadLocal(uidLocal)
                                    cw.callFunction("java/lang/Math", "floorDiv", "(II)I")
                                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                }
                            }
                            "%=" -> {
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                val uidLocal = cw.generateUniqueLocal("\$Score")
                                cw.storeLocal(uidLocal, "I") {
                                    loadLocal(uidLocal)
                                    cw.methodVisitor.visitJumpInsn(Opcodes.IFEQ, skip)
                                    cw.loadLocal(targetLocal)
                                    cw.loadLocal(targetLocal)
                                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                    loadLocal(uidLocal)
                                    cw.callFunction("java/lang/Math", "floorMod", "(II)I")
                                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                }
                            }
                            "<" -> {
                                cw.loadLocal(targetLocal)
                                cw.loadLocal(targetLocal)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.callFunction("java/lang/Math", "min", "(II)I")
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            ">" -> {
                                cw.loadLocal(targetLocal)
                                cw.loadLocal(targetLocal)
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                source()
                                cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                cw.callFunction("java/lang/Math", "max", "(II)I")
                                cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                            }
                            "><" -> {
                                cw.visitGetScoreboard()
                                sourceGetter()
                                cw.loadLocal(sourceObjective)
                                cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                                val sourceLocal = cw.generateUniqueLocal("\$ScoreSource")
                                cw.storeLocal(sourceLocal, "Lnet/minecraft/world/scores/Score;") {
                                    cw.loadLocal(sourceLocal)
                                    cw.loadLocal(targetLocal)
                                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                    cw.loadLocal(sourceLocal)
                                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                                    cw.loadLocal(targetLocal)
                                    cw.methodVisitor.visitInsn(Opcodes.SWAP)
                                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                    cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
                                }
                            }
                            else -> throw UnsupportedOperationException("Unknown operation operator: ${command.get<String>("operation")}")
                        }
                    }
                }
            }
            cw.methodVisitor.visitLabel(skip)
        }
    }
}

fun generate_scoreboard_players_remove(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    runScoreboardOperation(command["targets"], command["objective"], context, cw) {
        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
        cw.writeInstruction(Opcodes.DUP)
        cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
        cw.writeInt(command["score"])
        cw.writeInstruction(Opcodes.ISUB)
        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
    }
}

fun generate_scoreboard_players_reset(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    if(command.params["objective"] != null) {
        runScoreboardOperation(command["targets"], command["objective"], context, cw) {
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "resetPlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
        }
    }else {
        throw UnsupportedOperationException("TODO: implement /scoreboard players reset <targets> [<objective>]")
    }
}

fun generate_scoreboard_players_set(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    runScoreboardOperation(command["targets"], command["objective"], context, cw) {
        cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
        cw.writeInt(command["score"])
        cw.callMethod("net/minecraft/world/scores/Score", "setScore", "(I)V")
    }
}