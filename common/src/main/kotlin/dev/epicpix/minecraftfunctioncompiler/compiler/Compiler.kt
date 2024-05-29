package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.commands.Command
import dev.epicpix.minecraftfunctioncompiler.commands.CommandType
import dev.epicpix.minecraftfunctioncompiler.compiler.impl.CompilationContext
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCompareOperation
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardOperation
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.il.*

fun compile(commands: List<Command>): List<Instruction> {
    val instructions = mutableListOf<Instruction>()
    for(command in commands) {
        CompilationContext(addInstruction = instructions::add).compile(command)
    }
    return instructions
}

private fun CompilationContext.compile(command: Command?, result: ResultStorage = ResultStorage.empty()) {
    if(command == null) return
    when(command.type!!) {
        CommandType.EXECUTE_ALIGN -> {
            getPosition { positionId ->
                val newPosition = LocationData.ofLocal(highestLocal)
                val instruction = Instructions.alignPosition(positionId, newPosition, command["swizzle"])
                with(highestLocal = highestLocal + 1, currentPosition = newPosition, addInstruction = instruction.children::add).compile(command["command"], result)
                addInstruction(instruction)
            }
        }
        CommandType.EXECUTE_AS -> {
            forSelector(command["targets"]) { entity, _, _ ->
                with(currentEntity = entity).compile(command["command"], result)
            }
        }
        CommandType.EXECUTE_AT -> {
            forSelector(command["targets"]) { entity, _, _ ->
                val world = LocationData.ofLocal(highestLocal)
                val position = LocationData.ofLocal(highestLocal + 1)
                val rotation = LocationData.ofLocal(highestLocal + 2)
                val execInstruction = Instructions2.getEntityRotation(entity, rotation)
                val instruction = Instructions2.getEntityWorld(entity, world).with(Instructions2.getEntityPosition(entity, position).with(execInstruction))
                with(highestLocal = highestLocal + 3, currentWorld = world, currentPosition = position, currentRotation = rotation, addInstruction = execInstruction.children::add).compile(command["command"], result)
                addInstruction(instruction)
            }
        }
        CommandType.EXECUTE_IF_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").limit(1)) { _, _, _ ->
                compile(command["command"])
            }
        }
        CommandType.EXECUTE_IF_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").selectSingle()) { name ->
                    getScoreData(objective, name, false) { score ->
                        val instr = Instructions.checkInScoreRange(score, command["range"])
                        with(addInstruction = instr.children::add).compile(command["command"])
                        addInstruction(instr)
                    }
                }
            }
        }
        CommandType.EXECUTE_IF_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").selectSingle()) { targetName ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").selectSingle()) { sourceName ->
                            getScoreData(targetObjective, targetName, false) { target ->
                                getScoreData(sourceObjective, sourceName, false) { source ->
                                    val instr = Instructions.checkScoreCompare(target, source, command.get("comparison"))
                                    with(addInstruction = instr.children::add).compile(command["command"])
                                    addInstruction(instr)
                                }
                            }
                        }
                    }
                }
            }
        }
        CommandType.EXECUTE_POSITIONED_POS -> {
            forPosition(command["position"]) { position ->
                copy(currentPosition = position).compile(command["command"], result)
            }
        }
        CommandType.EXECUTE_RUN -> {
            compile(command["command"])
        }
        CommandType.EXECUTE_STORE_RESULT_SCORE -> {
            withResultStorage(requireResult = true) { res ->
                compile(command["command"], res)
                res.checkRequirements(command["command"])
                getObjective(command["objective"]) { objective ->
                    forScoreHolder(command["targets"]) { name ->
                        getScoreData(objective, name, true) { score ->
                            addInstruction(Instructions2.setScore(score, res.result!!))
                        }
                    }
                }
            }
        }
        CommandType.EXECUTE_UNLESS_ENTITY -> {
            forScope {  scope ->
                forSelector(command.get<SelectorArgument>("entities").limit(1)) { _, _, _ ->
                    addInstruction(Instructions2.exitScope(scope))
                }
                compile(command["command"])
            }
        }
        CommandType.EXECUTE_UNLESS_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").selectSingle()) { name ->
                    getScoreData(objective, name, false) { score ->
                        val instr = Instructions.checkNotInScoreRange(score, command["range"])
                        copy(addInstruction = instr.children::add).compile(command["command"])
                        addInstruction(instr)
                    }
                }
            }
        }
        CommandType.EXECUTE_UNLESS_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").selectSingle()) { targetName ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").selectSingle()) { sourceName ->
                            getScoreData(targetObjective, targetName, false) { target ->
                                getScoreData(sourceObjective, sourceName, false) { source ->
                                    val instr = Instructions.checkScoreCompare(target, source, command.get<ScoreboardCompareOperation>("comparison").negate())
                                    copy(addInstruction = instr.children::add).compile(command["command"])
                                    addInstruction(instr)
                                }
                            }
                        }
                    }
                }
            }
        }
        CommandType.FUNCTION -> {
            addInstruction(Instructions.callFunctions(command["functions"], currentEntity, currentWorld, currentPosition, currentRotation))
        }
        CommandType.GAMEMODE -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions.setGamemode(entity, command["gamemode"]))
            }
        }
        CommandType.KILL -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions2.killEntity(entity))
            }
        }
        CommandType.SCOREBOARD_PLAYERS_ADD -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { scoreData ->
                        addInstruction(Instructions2.addScore(scoreData, LocationData.ofInteger(command["score"])))
                    }
                }
            }
        }
        CommandType.SCOREBOARD_PLAYERS_GET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["target"]) { name ->
                    getScoreData(objective, name, true) { scoreData ->
                        val res = LocationData.ofLocal(highestLocal)
                        addInstruction(Instructions2.getScoreValue(scoreData, res).apply {
                            result.setResult(with(addInstruction = children::add), res)
                        })
                    }
                }
            }
        }
        CommandType.SCOREBOARD_PLAYERS_OPERATION -> {
            val exceptionScope = ScopeData.ofId(highestLocal)
            val self = when(command.get<ScoreboardOperation>("operation")) {
                ScoreboardOperation.DIV, ScoreboardOperation.MOD -> {
                    val instruction = Instructions2.scope(exceptionScope)
                    addInstruction(instruction)
                    with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add)
                }
                else -> this
            }
            self.getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command["targets"]) { targetName ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command["source"]) { sourceName ->
                            getScoreData(targetObjective, targetName, true) { target ->
                                getScoreData(sourceObjective, sourceName, true) { source ->
                                    when(command.get<ScoreboardOperation>("operation")!!) {
                                        ScoreboardOperation.SET -> addInstruction(Instructions2.runScoreOperationSet(target, source))
                                        ScoreboardOperation.ADD -> addInstruction(Instructions2.runScoreOperationPlus(target, source))
                                        ScoreboardOperation.SUB -> addInstruction(Instructions2.runScoreOperationMinus(target, source))
                                        ScoreboardOperation.MUL -> addInstruction(Instructions2.runScoreOperationMultiply(target, source))
                                        ScoreboardOperation.DIV -> addInstruction(Instructions2.runScoreOperationDivide(target, source, exceptionScope))
                                        ScoreboardOperation.MOD -> addInstruction(Instructions2.runScoreOperationModulo(target, source, exceptionScope))
                                        ScoreboardOperation.MIN -> addInstruction(Instructions2.runScoreOperationMin(target, source))
                                        ScoreboardOperation.MAX -> addInstruction(Instructions2.runScoreOperationMax(target, source))
                                        ScoreboardOperation.SWAP -> addInstruction(Instructions2.runScoreOperationSwap(target, source))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        CommandType.SCOREBOARD_PLAYERS_SET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { score ->
                        addInstruction(Instructions2.setScore(score, LocationData.ofInteger(command["score"])))
                    }
                }
            }
        }
        CommandType.SCOREBOARD_PLAYERS_REMOVE -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { score ->
                        addInstruction(Instructions2.removeScore(score, LocationData.ofInteger(command["score"])))
                    }
                }
            }
        }
        CommandType.SCOREBOARD_PLAYERS_RESET_ALL -> {
            forScoreHolder(command["targets"]) { name ->
                addInstruction(Instructions2.resetPlayerScore(name))
            }
        }
        CommandType.SCOREBOARD_PLAYERS_RESET_OBJECTIVE -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions2.resetScore(objective, name))
                }
            }
        }
        CommandType.TAG_ADD -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions2.addEntityTag(entity, command["name"]))
            }
        }
        CommandType.TAG_REMOVE -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions2.removeEntityTag(entity, command["name"]))
            }
        }
    }
}