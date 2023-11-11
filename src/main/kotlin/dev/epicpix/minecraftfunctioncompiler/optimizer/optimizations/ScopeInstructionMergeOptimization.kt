package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

object ScopeInstructionMergeOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions)
    }

    private fun runOptimizations(instructions: InstructionList): InstructionList {
        val results = mutableListOf<Instruction>()
        var checkIndex = 0
        while(checkIndex < instructions.size) {
            var index = checkIndex
            val compareBase = instructions[checkIndex]
            val newBase = Instruction(compareBase.type, compareBase.data, compareBase.children.toMutableList())
            index++
            while(index < instructions.size) {
                val compare = instructions[index]
                if(compareBase.type != compare.type || compareBase.data != compare.data) break
                when(compare.type) {
                    InstructionType.GET_CURRENT_ENTITY -> {}
                    InstructionType.IF_NOT_NULL -> {}
                    InstructionType.GET_SCOREBOARD_NAME -> {}
                    InstructionType.GET_OBJECTIVE -> {}
                    InstructionType.SCORE_DATA -> {}
                    else -> break
                }
                newBase.children.addAll(compare.children)
                index++
            }
            results.add(newBase.copy(children = runOptimizations(newBase.children).toMutableList()))
            checkIndex = index
        }
        return results
    }

}