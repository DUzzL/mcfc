package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.parser.CommandType.*
import net.minecraft.resources.ResourceLocation

fun compile(commands: List<Command>): List<Instruction> {
    val instructions = mutableListOf<Instruction>()
    for(command in commands) {
        CompilationContext(addInstruction = instructions::add).compile(command)
    }
    return instructions
}

private fun CompilationContext.compile(command: Command, result: ResultStorage = ResultStorage()) {
    when(command.type) {
        ATTRIBUTE_BASE_SET -> {
            forSelector(command["target"]) { entity, _, _ ->
                val attrLocation = LocationData(local = highestLocal)
                val instruction =
                Instructions.checkEntityTypes(entity, listOf(ResourceLocation("minecraftfunctioncompiler", "living_entity")), false)
                    .with(Instructions.getAttributeInstance(entity, attrLocation, command["attribute"])
                        .with(Instructions.ifNotNull(attrLocation)
                            .with(Instructions.setAttributeBase(attrLocation, command["value"]))))
                addInstruction(instruction)
            }
        }
        EXECUTE_ALIGN -> {
            getPosition { positionId ->
                val newPosition = LocationData(local = highestLocal)
                val instruction = Instructions.alignPosition(positionId, newPosition, command["swizzle"])
                copy(highestLocal = highestLocal + 1, currentPosition = newPosition, addInstruction = instruction.children::add).compile(command["command"], result)
                addInstruction(instruction)
            }
        }
        EXECUTE_AS -> {
            forSelector(command["targets"]) { entity, _, _ ->
                copy(currentEntity = entity).compile(command["command"], result)
            }
        }
        EXECUTE_AT -> {
            forSelector(command["targets"]) { entity, _, _ ->
                val world = LocationData(local = highestLocal)
                val position = LocationData(local = highestLocal + 1)
                val rotation = LocationData(local = highestLocal + 2)
                val execInstruction = Instructions.getEntityRotation(entity, rotation)
                val instruction = Instructions.getEntityWorld(entity, world).with(Instructions.getEntityPosition(entity, position).with(execInstruction))
                copy(highestLocal = highestLocal + 3, currentWorld = world, currentPosition = position, currentRotation = rotation, addInstruction = execInstruction.children::add).compile(command["command"], result)
                addInstruction(instruction)
            }
        }
        EXECUTE_IF_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { _, _, _ ->
                compile(command["command"])
            }
        }
        EXECUTE_IF_SCORE_RANGE -> {
            getObjective(command["targetObjective"]) { objective ->
                forScoreHolder(command.get<ScoreHolderArgument>("target").single()) { name ->
                    getScoreData(objective, name, false) { score ->
                        val instr = Instructions.checkScoreRange(score, command["range"], false)
                        copy(addInstruction = instr.children::add).compile(command["command"])
                        addInstruction(instr)
                    }
                }
            }
        }
        EXECUTE_IF_SCORE_COMPARE -> {
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
                                    copy(addInstruction = instr.children::add).compile(command["command"])
                                    addInstruction(instr)
                                }
                            }
                        }
                    }
                }
            }
        }
        EXECUTE_POSITIONED_POS -> {
            forPosition(command["position"]) { position ->
                copy(currentPosition = position).compile(command["command"], result)
            }
        }
        EXECUTE_STORE_RESULT_SCORE -> {
            withResultStorage(requireResult = true) { res ->
                compile(command["command"], res)
                res.checkRequirements(command["command"])
                getObjective(command["objective"]) { objective ->
                    forScoreHolder(command["targets"]) { name ->
                        getScoreData(objective, name, true) { score ->
                            addInstruction(Instructions.setScore(score, res.result!!))
                        }
                    }
                }
            }
        }
        EXECUTE_UNLESS_ENTITY -> {
            forSelector(command.get<SelectorArgument>("entities").copy(limit = 1)) { _, breakScope, _ ->
                addInstruction(Instructions.exitScope(breakScope))
            }
            compile(command["command"])
        }
        EXECUTE_UNLESS_SCORE_RANGE -> {
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
        EXECUTE_UNLESS_SCORE_COMPARE -> {
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
        FUNCTION -> {
            addInstruction(Instructions.callFunctions(command["functions"], currentEntity, currentWorld, currentPosition, currentRotation))
        }
        GAMEMODE -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions.setGamemode(entity, command["gamemode"]))
            }
        }
        KILL -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions.killEntity(entity))
            }
        }
        SCOREBOARD_PLAYERS_ADD -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { scoreData ->
                        addInstruction(Instructions.addScore(scoreData, LocationData(integer = command["score"])))
                    }
                }
            }
        }
        SCOREBOARD_PLAYERS_GET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["target"]) { name ->
                    getScoreData(objective, name, true) { scoreData ->
                        val res = LocationData(local = highestLocal)
                        addInstruction(Instructions.getScoreValue(scoreData, res).apply {
                            result.setResult(this@getScoreData.copy(addInstruction = children::add), res)
                        })
                    }
                }
            }
        }
        SCOREBOARD_PLAYERS_OPERATION -> {
            forEmptyCondition { exceptionScope ->
                getObjective(command["targetObjective"]) { targetObjective ->
                    forScoreHolder(command["targets"]) { targetName ->
                        getObjective(command["sourceObjective"]) { sourceObjective ->
                            forScoreHolder(command["source"]) { sourceName ->
                                getScoreData(targetObjective, targetName, true) { target ->
                                    getScoreData(sourceObjective, sourceName, true) { source ->
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
                                        addInstruction(Instructions.runScoreOperation(target, source, operation, exceptionScope))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SCOREBOARD_PLAYERS_SET -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { score ->
                        addInstruction(Instructions.setScore(score, LocationData(integer = command["score"])))
                    }
                }
            }
        }
        SCOREBOARD_PLAYERS_REMOVE -> {
            getObjective(command["objective"]) { objective ->
                forScoreHolder(command["targets"]) { name ->
                    getScoreData(objective, name, true) { score ->
                        addInstruction(Instructions.removeScore(score, LocationData(integer = command["score"])))
                    }
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
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions.addEntityTag(entity, command["name"]))
            }
        }
        TAG_REMOVE -> {
            forSelector(command["targets"]) { entity, _, _ ->
                addInstruction(Instructions.removeEntityTag(entity, command["name"]))
            }
        }
        EMPTY -> {}
    }
}