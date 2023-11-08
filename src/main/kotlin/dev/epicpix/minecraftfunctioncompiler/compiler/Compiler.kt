package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.parser.CommandType.*

fun compile(commands: List<Command>): List<Instruction> {
    val instructions = mutableListOf<Instruction>()
    for(command in commands) {
        CompilationContext(addInstruction = instructions::add).compile(command)
    }
    return instructions
}

private fun CompilationContext.compile(command: Command) {
    when(command.type) {
        EXECUTE_AS -> {
            forSelector(command["targets"]) { entity, breakId, continueId ->
                copy(currentEntity = entity).compile(command["command"])
            }
        }
        EXECUTE_AT -> {
            forSelector(command["targets"]) { entity, breakId, continueId ->
                val world = LocationData(local = highestLocal)
                val position = LocationData(local = highestLocal + 1)
                val rotation = LocationData(local = highestLocal + 2)
                val execInstruction = Instructions.getEntityRotation(entity, rotation)
                val instruction = Instructions.getEntityWorld(entity, world).with(Instructions.getEntityPosition(entity, position).with(execInstruction))
                copy(highestLocal = highestLocal + 3, currentWorld = world, currentPosition = position, currentRotation = rotation, addInstruction = execInstruction.children::add).compile(command["command"])
                addInstruction(instruction)
            }
        }
        EXECUTE_IF_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { entity, breakId, continueId ->
                compile(command["command"])
            }
        }
        EXECUTE_IF_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { name ->
                    val instr = Instructions.checkScoreRange(objective, name, command["range"], false)
                    copy(addInstruction = instr.children::add).compile(command["command"])
                    addInstruction(instr)
                }
            }
        }
        EXECUTE_IF_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { target ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").single()) { source ->
                            val operation = when(command.get<String>("comparison")) {
                                ">" -> ScoreboardCompareOperation.GT
                                ">=" -> ScoreboardCompareOperation.GE
                                "=" -> ScoreboardCompareOperation.EQ
                                "<" -> ScoreboardCompareOperation.LT
                                "<=" -> ScoreboardCompareOperation.LE
                                else -> throw UnsupportedOperationException("Unknown comparison: ${command.get<String>("comparison")}")
                            }
                            val instr = Instructions.checkScoreCompare(targetObjective, target, sourceObjective, source, operation)
                            copy(addInstruction = instr.children::add).compile(command["command"])
                            addInstruction(instr)
                        }
                    }
                }
            }
        }
        EXECUTE_POSITIONED_POS -> {
            forPosition(command["position"]) { position ->
                copy(currentPosition = position).compile(command["command"])
            }
        }
        EXECUTE_UNLESS_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { local, breakId, continueId ->
                addInstruction(Instructions.breakLoop(breakId))
            }
            compile(command["command"])
        }
        EXECUTE_UNLESS_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { name ->
                    val instr = Instructions.checkScoreRange(objective, name, command["range"], true)
                    copy(addInstruction = instr.children::add).compile(command["command"])
                    addInstruction(instr)
                }
            }
        }
        EXECUTE_UNLESS_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { target ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").single()) { source ->
                            val operation = when(command.get<String>("comparison")) {
                                ">" -> ScoreboardCompareOperation.LE
                                ">=" -> ScoreboardCompareOperation.LT
                                "=" -> ScoreboardCompareOperation.NE
                                "<" -> ScoreboardCompareOperation.GE
                                "<=" -> ScoreboardCompareOperation.GT
                                else -> throw UnsupportedOperationException("Unknown comparison: ${command.get<String>("comparison")}")
                            }
                            val instr = Instructions.checkScoreCompare(targetObjective, target, sourceObjective, source, operation)
                            copy(addInstruction = instr.children::add).compile(command["command"])
                            addInstruction(instr)
                        }
                    }
                }
            }
        }
        FUNCTION -> {
            addInstruction(Instructions.callFunctions(command["functions"], currentEntity, currentWorld, currentPosition, currentRotation))
        }
        KILL -> {
            forSelector(command["targets"]) { entity, breakId, continueId ->
                addInstruction(Instructions.killEntity(entity))
            }
        }
        SCOREBOARD_PLAYERS_ADD -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions.addScore(objective, name, command["score"]))
                }
            }
        }
        SCOREBOARD_PLAYERS_OPERATION -> {
            forEmptyCondition { loopId ->
                getObjective(command["targetObjective"]) { targetObjective ->
                    forScoreHolder(command["targets"]) { targetName ->
                        getObjective(command["sourceObjective"]) { sourceObjective ->
                            forScoreHolder(command["source"]) { sourceName ->
                                val operation = when(command.get<String>("operation")) {
                                    "=" -> ScoreboardOperation.SET
                                    "+=" -> ScoreboardOperation.PLUS
                                    "-=" -> ScoreboardOperation.MINUS
                                    "*=" -> ScoreboardOperation.MULTIPLY
                                    "/=" -> ScoreboardOperation.DIVIDE
                                    "%=" -> ScoreboardOperation.MODULO
                                    "<" -> ScoreboardOperation.MIN
                                    ">" -> ScoreboardOperation.MAX
                                    "><" -> ScoreboardOperation.SWAP
                                    else -> throw UnsupportedOperationException("Unknown operation: ${command.get<String>("operation")}")
                                }
                                addInstruction(Instructions.runScoreOperation(targetObjective, targetName, sourceObjective, sourceName, operation, loopId))
                            }
                        }
                    }
                }
            }
        }
        SCOREBOARD_PLAYERS_SET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions.setScore(objective, name, command["score"]))
                }
            }
        }
        SCOREBOARD_PLAYERS_REMOVE -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions.addScore(objective, name, -command.get<Int>("score")))
                }
            }
        }
        SCOREBOARD_PLAYERS_RESET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions.resetScore(objective, name))
                }
            }
        }
        TAG_ADD -> {
            forSelector(command["targets"]) { entity, breakId, continueId ->
                addInstruction(Instructions.addEntityTag(entity, command["name"]))
            }
        }
        TAG_REMOVE -> {
            forSelector(command["targets"]) { entity, breakId, continueId ->
                addInstruction(Instructions.removeEntityTag(entity, command["name"]))
            }
        }
    }
}