package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.Instruction.remapInstructionData
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
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
        val data = remapInstructionData(instruction.data, cse.mapping)

        when(instruction.type) {
            InstructionTypes2.GET_OBJECTIVE -> {
                val name = instruction.get<String>("name")
                val objective = instruction.get<LocationData>("objective")
                if(newCse.objectives[name] == null) {
                    newCse = newCse.copy(
                        objectives = newCse.objectives.toMutableMap().apply { set(name, objective.local!!) }
                    )
                }else {
                    return runOptimizations(instruction.children, newCse.copy(
                        mapping = newCse.mapping.toMutableMap().apply { set(objective.local!!, newCse.objectives[name]!!) }
                    ))
                }
            }
            InstructionTypes2.GET_SCOREBOARD_NAME -> {
                val entity = instruction.get<LocationData>("entity")
                val name = instruction.get<LocationData>("name")
                if(newCse.scoreboardNames[entity.local!!] == null) {
                    newCse = newCse.copy(
                        scoreboardNames = newCse.scoreboardNames.toMutableMap().apply { set(entity.local, name.local!!) }
                    )
                }else {
                    return runOptimizations(instruction.children, newCse.copy(
                        mapping = newCse.mapping.toMutableMap().apply { set(name.local!!, newCse.scoreboardNames[entity.local]!!) }
                    ))
                }
            }
            else -> {}
        }

        return listOf(Instruction(instruction.type, data, runOptimizations(instruction.children, newCse).toMutableList()))
    }

}
