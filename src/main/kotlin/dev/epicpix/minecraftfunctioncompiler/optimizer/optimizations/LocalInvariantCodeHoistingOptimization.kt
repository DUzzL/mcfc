package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

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
        if(child.type == InstructionType.GET_CURRENT_ENTITY) return true
        if(child.type == InstructionType.IF_NOT_NULL || child.type == InstructionType.IF_NULL) {
            if((child.data as InstructionDataLocation).local != getVariableDefinition(instruction)) return true
        }
        return false
    }

    private fun getVariableDefinition(instruction: Instruction): LocationData? {
        return when(instruction.type) {
            InstructionType.GET_CURRENT_ENTITY -> (instruction.data as InstructionDataLocation).local
            InstructionType.LIMITER -> LocationData(local = (instruction.data as InstructionDataIntLoop).loopId)
            InstructionType.FOR_EACH_PLAYER -> (instruction.data as InstructionDataLocation).local
            InstructionType.FOR_EACH_ENTITY -> (instruction.data as InstructionDataLocation).local
            InstructionType.FOR_PLAYER -> (instruction.data as InstructionDataLocationString).local
            InstructionType.FOR_ENTITY -> (instruction.data as InstructionDataLocationUUID).local
            InstructionType.FOR_SCORE_HOLDERS -> (instruction.data as InstructionDataLocationBoolean).local
            InstructionType.GET_SCOREBOARD_NAME -> (instruction.data as InstructionDataLocationLocation).local2
            InstructionType.GET_OBJECTIVE -> (instruction.data as InstructionDataLocationString).local
            InstructionType.SCORE_DATA -> (instruction.data as InstructionDataLocationLocationLocation).local3
            else -> null
        }
    }

}