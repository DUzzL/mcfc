package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

private data class CommonSubexpressionEliminationData(
    val mapping: Map<Int, Int>,
    val objectives: Map<String, Int>,
    val scoreboardNames: Map<Int, Int>,
)

object CommonSubexpressionEliminationOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions, CommonSubexpressionEliminationData(mapOf(), mapOf(), mapOf()))
    }

    private fun runOptimizations(instructions: InstructionList, cse: CommonSubexpressionEliminationData): InstructionList {
        val results = mutableListOf<Instruction>()
        instructions.forEach { results.addAll(runOptimizations(it, cse)) }
        return results
    }

    private fun runOptimizations(instruction: Instruction, cse: CommonSubexpressionEliminationData): InstructionList {
        var newCse = cse
        val data = when(instruction.data) {
            is InstructionDataLocation -> {
                val remapped = remapLocation(instruction.data.local, newCse)
                InstructionDataLocation(remapped)
            }
            is InstructionDataLocationString -> {
                val remapped = remapLocation(instruction.data.local, newCse)
                InstructionDataLocationString(remapped, instruction.data.value)
            }
            is InstructionDataLocationBoolean -> {
                val remapped = remapLocation(instruction.data.local, newCse)
                InstructionDataLocationBoolean(remapped, instruction.data.value)
            }
            is InstructionDataLocationLocation -> {
                val remapped1 = remapLocation(instruction.data.local, newCse)
                val remapped2 = remapLocation(instruction.data.local2, newCse)
                InstructionDataLocationLocation(remapped1, remapped2)
            }
            is InstructionDataLocationInt -> {
                val remapped1 = remapLocation(instruction.data.local, newCse)
                InstructionDataLocationInt(remapped1, instruction.data.value)
            }
            is InstructionDataLocationResources -> {
                val remapped1 = remapLocation(instruction.data.local, newCse)
                InstructionDataLocationResources(remapped1, instruction.data.resources)
            }
            is InstructionDataLocationLocationLocation -> {
                val remapped1 = remapLocation(instruction.data.local, newCse)
                val remapped2 = remapLocation(instruction.data.local2, newCse)
                val remapped3 = remapLocation(instruction.data.local3, newCse)
                InstructionDataLocationLocationLocation(remapped1, remapped2, remapped3)
            }
            is InstructionDataRunScoreOperation -> {
                val target = remapLocation(instruction.data.target, newCse)
                val source = remapLocation(instruction.data.source, newCse)
                InstructionDataRunScoreOperation(target, source, instruction.data.operation, instruction.data.exceptionLoopId)
            }
            is InstructionDataFunctionCall -> {
                val entity = remapLocation(instruction.data.entity, newCse)
                val world = remapLocation(instruction.data.world, newCse)
                val position = remapLocation(instruction.data.position, newCse)
                val rotation = remapLocation(instruction.data.rotation, newCse)
                InstructionDataFunctionCall(instruction.data.functions, entity, world, position, rotation)
            }
            is InstructionDataCheckScoreRange -> {
                val objective = remapLocation(instruction.data.score, newCse)
                InstructionDataCheckScoreRange(objective, instruction.data.range, instruction.data.negate)
            }
            is InstructionDataCheckScoreCompare -> {
                val target = remapLocation(instruction.data.target, newCse)
                val source = remapLocation(instruction.data.source, newCse)
                InstructionDataCheckScoreCompare(target, source, instruction.data.compareOperation)
            }
            is InstructionDataLocationLocationWorldCoordinates -> {
                val remapped1 = remapLocation(instruction.data.local, newCse)
                val remapped2 = remapLocation(instruction.data.local2, newCse)
                InstructionDataLocationLocationWorldCoordinates(remapped1, remapped2, instruction.data.coords)
            }
            else -> instruction.data
        }

        when(instruction.type) {
            InstructionType.GET_OBJECTIVE -> {
                data as InstructionDataLocationString
                if(newCse.objectives[data.value] == null) {
                    newCse = newCse.copy(
                        objectives = newCse.objectives.toMutableMap().apply { set(data.value, data.local.local!!) }
                    )
                }else {
                    return runOptimizations(instruction.children, newCse.copy(
                        mapping = newCse.mapping.toMutableMap().apply { set(data.local.local!!, newCse.objectives[data.value]!!) }
                    ))
                }
            }
            InstructionType.GET_SCOREBOARD_NAME -> {
                data as InstructionDataLocationLocation
                if(newCse.scoreboardNames[data.local.local!!] == null) {
                    newCse = newCse.copy(
                        scoreboardNames = newCse.scoreboardNames.toMutableMap().apply { set(data.local.local, data.local2.local!!) }
                    )
                }else {
                    return runOptimizations(instruction.children, newCse.copy(
                        mapping = newCse.mapping.toMutableMap().apply { set(data.local2.local!!, newCse.scoreboardNames[data.local.local]!!) }
                    ))
                }
            }
            else -> {}
        }

        return listOf(Instruction(instruction.type, data, runOptimizations(instruction.children, newCse).toMutableList()))
    }

}

private fun remapLocation(location: LocationData, cse: CommonSubexpressionEliminationData): LocationData {
    if(location.local != null) {
        val remappedLocal = cse.mapping[location.local] ?: location.local
        return LocationData(local = remappedLocal)
    }
    return location
}

@JvmName("remapLocationNullable")
private fun remapLocation(location: LocationData?, cse: CommonSubexpressionEliminationData): LocationData? {
    if(location?.local != null) {
        val remappedLocal = cse.mapping[location.local] ?: location.local
        return LocationData(local = remappedLocal)
    }
    return location
}