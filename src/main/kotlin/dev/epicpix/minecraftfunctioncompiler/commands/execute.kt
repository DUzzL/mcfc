package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.*
import dev.epicpix.minecraftfunctioncompiler.CodeGenerator.generateCommandLike
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Opcodes

fun generate_execute_as(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
        val entityUid = cw.generateUniqueLocal("CurrentEntity")
        val newContext = context.copy(entityLocal = entityUid, originalEntity = context.originalEntity && command.get<SelectorArgument>("targets").self)
        getter()
        cw.storeLocal(entityUid, "Lnet/minecraft/world/entity/Entity;") {
            generateCommandLike(command["command"], newContext, cw, definedFunctions)
        }
    }
}

fun generate_execute_if_entity(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateIfEntityExists(cw, command["entities"], context) {
        generateCommandLike(command["command"], context, cw, definedFunctions)
    }
}

fun generate_execute_if_score_compare(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
        context.runWithObjective(cw, command["sourceObjective"]) { sourceObjective, context ->
            visitGetSingleScoreHolder(cw, context, command["target"]) { getterTarget ->
                visitGetSingleScoreHolder(cw, context, command["source"]) { sourceSource ->
                    cw.visitGetScoreboard()
                    getterTarget()
                    cw.loadLocal(targetObjective)
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.visitGetScoreboard()
                    sourceSource()
                    cw.loadLocal(sourceObjective)
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")

                    val operator = command.get<String>("comparison")

                    when(operator) {
                        "<" -> {
                            cw.executeIf(Opcodes.IF_ICMPLT) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        "<=" -> {
                            cw.executeIf(Opcodes.IF_ICMPLE) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        "=" -> {
                            cw.executeIf(Opcodes.IF_ICMPEQ) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        ">" -> {
                            cw.executeIf(Opcodes.IF_ICMPGT) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        ">=" -> {
                            cw.executeIf(Opcodes.IF_ICMPGE) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        else -> throw UnsupportedOperationException("Unknown score comparison operator: $operator")
                    }
                }
            }
        }
    }
}

fun generate_execute_if_score_range(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
        visitGetSingleScoreHolder(cw, context, command["target"]) { getter ->
            cw.visitGetScoreboard()
            getter()
            cw.loadLocal(targetObjective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
            val uidLocal = cw.generateUniqueLocal("\$Score")
            cw.storeLocal(uidLocal, "I") {
                val range = command.get<IntRangeArgument>("range")
                if (range.minimum != null && range.maximum == null) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPGE) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.maximum != null && range.minimum == null) {
                    loadLocal(uidLocal)
                    writeInt(range.maximum)
                    executeIf(Opcodes.IF_ICMPLE) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.minimum != null && range.minimum == range.maximum) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPEQ) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.minimum != null && range.maximum != null) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPGE) {
                        loadLocal(uidLocal)
                        writeInt(range.maximum)
                        executeIf(Opcodes.IF_ICMPLE) {
                            generateCommandLike(command["command"], context, cw, definedFunctions)
                        }
                    }
                }
            }
        }
    }
}

fun generate_execute_unless_entity(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateUnlessEntityExists(cw, command["entities"], context) {
        generateCommandLike(command["command"], context, cw, definedFunctions)
    }
}

fun generate_execute_unless_score_compare(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
        context.runWithObjective(cw, command["sourceObjective"]) { sourceObjective, context ->
            visitGetSingleScoreHolder(cw, context, command["target"]) { getterTarget ->
                visitGetSingleScoreHolder(cw, context, command["source"]) { sourceSource ->
                    cw.visitGetScoreboard()
                    getterTarget()
                    cw.loadLocal(targetObjective)
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
                    cw.visitGetScoreboard()
                    sourceSource()
                    cw.loadLocal(sourceObjective)
                    cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
                    cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")

                    val operator = command.get<String>("comparison")

                    when(operator) {
                        "<" -> {
                            cw.executeIf(Opcodes.IF_ICMPGE) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        "<=" -> {
                            cw.executeIf(Opcodes.IF_ICMPGT) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        "=" -> {
                            cw.executeIf(Opcodes.IF_ICMPNE) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        ">" -> {
                            cw.executeIf(Opcodes.IF_ICMPLE) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        ">=" -> {
                            cw.executeIf(Opcodes.IF_ICMPLT) {
                                generateCommandLike(command["command"], context, cw, definedFunctions)
                            }
                        }
                        else -> throw UnsupportedOperationException("Unknown score comparison operator: $operator")
                    }
                }
            }
        }
    }
}

fun generate_execute_unless_score_range(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    context.runWithObjective(cw, command["targetObjective"]) { targetObjective, context ->
        visitGetSingleScoreHolder(cw, context, command["target"]) { getter ->
            cw.visitGetScoreboard()
            getter()
            cw.loadLocal(targetObjective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getOrCreatePlayerScore", "(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
            cw.callMethod("net/minecraft/world/scores/Score", "getScore", "()I")
            val uidLocal = cw.generateUniqueLocal("\$Score")
            cw.storeLocal(uidLocal, "I") {
                val range = command.get<IntRangeArgument>("range")
                if (range.minimum != null && range.maximum == null) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPLT) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.maximum != null && range.minimum == null) {
                    loadLocal(uidLocal)
                    writeInt(range.maximum)
                    executeIf(Opcodes.IF_ICMPGT) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.minimum != null && range.minimum == range.maximum) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPNE) {
                        generateCommandLike(command["command"], context, cw, definedFunctions)
                    }
                } else if (range.minimum != null && range.maximum != null) {
                    loadLocal(uidLocal)
                    writeInt(range.minimum)
                    executeIf(Opcodes.IF_ICMPLT) {
                        loadLocal(uidLocal)
                        writeInt(range.maximum)
                        executeIf(Opcodes.IF_ICMPGT) {
                            generateCommandLike(command["command"], context, cw, definedFunctions)
                        }
                    }
                }
            }
        }
    }
}