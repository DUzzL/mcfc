package dev.epicpix.minecraftfunctioncompiler.v1_20_4.compiler

import dev.epicpix.minecraftfunctioncompiler.commands.CommandType
import dev.epicpix.minecraftfunctioncompiler.compiler.ResultStorage
import dev.epicpix.minecraftfunctioncompiler.compiler.impl.CompilationContext
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command

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
                val instruction = Instructions.getEntityWorld(entity, world).with(Instructions2.getEntityPosition(entity, position).with(execInstruction))
                with(highestLocal = highestLocal + 3, currentWorld = world, currentPosition = position, currentRotation = rotation, addInstruction = execInstruction.children::add).compile(command["command"], result)
                addInstruction(instruction)
            }
        }
        CommandType.EXECUTE_IF_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { _, _, _ ->
                compile(command["command"])
            }
        }
        CommandType.EXECUTE_IF_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { name ->
                    getScoreData(objective, name, false) { score ->
                        val instr = Instructions.checkScoreRange(score, command["range"], false)
                        with(addInstruction = instr.children::add).compile(command["command"])
                        addInstruction(instr)
                    }
                }
            }
        }
        CommandType.EXECUTE_IF_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { targetName ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").single()) { sourceName ->
                            getScoreData(targetObjective, targetName, false) { target ->
                                getScoreData(sourceObjective, sourceName, false) { source ->
                                    val operation = when(command.get<String>("comparison")) {
                                        ">" -> ScoreboardCompareOperation.GT
                                        ">=" -> ScoreboardCompareOperation.GE
                                        "=" -> ScoreboardCompareOperation.EQ
                                        "<" -> ScoreboardCompareOperation.LT
                                        "<=" -> ScoreboardCompareOperation.LE
                                        else -> throw UnsupportedOperationException("Unknown comparison: ${command.get<String>("comparison")}")
                                    }
                                    val instr = Instructions.checkScoreCompare(target, source, operation)
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
                forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { _, _, _ ->
                    addInstruction(Instructions2.exitScope(scope))
                }
                compile(command["command"])
            }
        }
        CommandType.EXECUTE_UNLESS_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { name ->
                    getScoreData(objective, name, false) { score ->
                        val instr = Instructions.checkScoreRange(score, command["range"], true)
                        copy(addInstruction = instr.children::add).compile(command["command"])
                        addInstruction(instr)
                    }
                }
            }
        }
        CommandType.EXECUTE_UNLESS_SCORE_COMPARE -> {
            getObjective(command["targetObjective"]) { targetObjective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { targetName ->
                    getObjective(command["sourceObjective"]) { sourceObjective ->
                        forScoreHolder(command.get<ScoreHolderArgument>("source").single()) { sourceName ->
                            getScoreData(targetObjective, targetName, false) { target ->
                                getScoreData(sourceObjective, sourceName, false) { source ->
                                    val operation = when(command.get<String>("comparison")) {
                                        ">" -> ScoreboardCompareOperation.LE
                                        ">=" -> ScoreboardCompareOperation.LT
                                        "=" -> ScoreboardCompareOperation.NE
                                        "<" -> ScoreboardCompareOperation.GE
                                        "<=" -> ScoreboardCompareOperation.GT
                                        else -> throw UnsupportedOperationException("Unknown comparison: ${command.get<String>("comparison")}")
                                    }
                                    val instr = Instructions.checkScoreCompare(target, source, operation)
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
            val self = when(command.get<String>("operation")) {
                "/=", "%=" -> {
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
                                    when(command.get<String>("operation")) {
                                        "=" -> addInstruction(Instructions2.runScoreOperationSet(target, source))
                                        "+=" -> addInstruction(Instructions2.runScoreOperationPlus(target, source))
                                        "-=" -> addInstruction(Instructions2.runScoreOperationMinus(target, source))
                                        "*=" -> addInstruction(Instructions2.runScoreOperationMultiply(target, source))
                                        "/=" -> addInstruction(Instructions2.runScoreOperationDivide(target, source, exceptionScope))
                                        "%=" -> addInstruction(Instructions2.runScoreOperationModulo(target, source, exceptionScope))
                                        "<" -> addInstruction(Instructions2.runScoreOperationMin(target, source))
                                        ">" -> addInstruction(Instructions2.runScoreOperationMax(target, source))
                                        "><" -> addInstruction(Instructions2.runScoreOperationSwap(target, source))
                                        else -> throw UnsupportedOperationException("Unknown operation: ${command.get<String>("operation")}")
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
        CommandType.SCOREBOARD_PLAYERS_RESET -> {
            if(command.params.containsKey("objective")) {
                getObjective(command["objective"]) { objective ->
                    forScoreHolder(command["targets"]) { name ->
                        addInstruction(Instructions2.resetScore(objective, name))
                    }
                }
            }else {
                forScoreHolder(command["targets"]) { name ->
                    addInstruction(Instructions2.resetPlayerScore(name))
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