package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

// TODO: Rework optimization to make different instructions have different weights, this will make sure that this instruction won't get stuck in a loop
object LocalInvariantCodeHoistingOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions)
    }

    private fun runOptimizations(instructions: InstructionList): InstructionList {
        val results = mutableListOf<Instruction>()
        instructions.forEach { results.addAll(runOptimizations(it)) }
        return results
    }

    private fun runOptimizations(instruction: Instruction): InstructionList {
        if(checkSwap(instruction)) {
            val instruction2 = instruction.children[0]
            val newInstruction = Instruction(instruction.type, instruction.data, runOptimizations(instruction2.children).toMutableList())
            val newInstruction2 = Instruction(instruction2.type, instruction2.data, mutableListOf(newInstruction))
            return listOf(newInstruction2)
        }
        return listOf(Instruction(instruction.type, instruction.data, runOptimizations(instruction.children).toMutableList()))
    }

    private fun checkSwap(instruction: Instruction): Boolean {
        if(instruction.children.size != 1) return false
        val child = instruction.children[0]
        if(child.type == InstructionTypes.GET_CURRENT_ENTITY) return true
        if(child.type == InstructionTypes.IF_NOT_NULL) {
            if(child.get<LocationData>("local") != getVariableDefinition(instruction)) return true
        }
        return false
    }

    private fun getVariableDefinition(instruction: Instruction): LocationData? {
        for((_, value) in instruction.data) {
            if(value is LocationData && value.isDestination) {
                return value
            }
        }
        return null
    }

}