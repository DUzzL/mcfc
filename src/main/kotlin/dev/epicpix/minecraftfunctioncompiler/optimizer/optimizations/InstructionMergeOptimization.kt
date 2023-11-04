package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionDataLocationLocationInt
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

object InstructionMergeOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions)
    }

    private fun runOptimizations(instructions: InstructionList): InstructionList {
        val results = mutableListOf<Instruction>()
        var checkIndex = 0
        while(checkIndex < instructions.size) {
            var index = checkIndex
            val compareBase = instructions[checkIndex]
            index++
            if(compareBase.type == InstructionType.ADD_SCORE) {
                var data = compareBase.data as InstructionDataLocationLocationInt
                while(index < instructions.size) {
                    val compare = instructions[index]
                    if(compareBase.type != compare.type) break
                    val otherData = compare.data as InstructionDataLocationLocationInt
                    if(otherData.local != data.local || otherData.local2 != data.local2) break
                    data = data.copy(value = data.value + otherData.value)
                    index++
                }
                results.add(Instruction(InstructionType.ADD_SCORE, data, mutableListOf()))
            }else if(compareBase.type == InstructionType.SET_SCORE) {
                var data = compareBase.data as InstructionDataLocationLocationInt
                while(index < instructions.size) {
                    val compare = instructions[index]
                    if(compareBase.type != compare.type) break
                    val otherData = compare.data as InstructionDataLocationLocationInt
                    if(otherData.local != data.local || otherData.local2 != data.local2) break
                    data = otherData
                    index++
                }
                results.add(Instruction(InstructionType.SET_SCORE, data, mutableListOf()))
            }else {
                results.add(compareBase.copy(children = runOptimizations(compareBase.children).toMutableList()))
            }
            checkIndex = index
        }
        return results
    }

}