package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2
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
                    InstructionTypes2.GET_CURRENT_ENTITY -> {}
                    InstructionTypes2.IF_NOT_NULL -> {}
                    InstructionTypes2.GET_SCOREBOARD_NAME -> {}
                    InstructionTypes2.GET_OBJECTIVE -> {}
                    InstructionTypes2.SCORE_DATA_AUTO_CREATE -> {}
                    InstructionTypes2.SCORE_DATA_NO_AUTO_CREATE -> {}
                    else -> break
                }
                newBase.children.addAll(compare.children)
                index++
            }
            results.add(newBase.withChildren(ArrayList(runOptimizations(newBase.children))))
            checkIndex = index
        }
        return results
    }

}