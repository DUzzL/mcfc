package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import dev.epicpix.minecraftfunctioncompiler.il.Instructions
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
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
                val score = compareBase.get<LocationData>("score")
                var value = compareBase.get<Int>("value")
                while(index < instructions.size) {
                    val compare = instructions[index]
                    if(compareBase.type != compare.type) break
                    if(score != compare.get<LocationData>("score")) break
                    value += compare.get<Int>("value")
                    index++
                }
                results.add(Instructions.addScore(score, value))
            }else if(compareBase.type == InstructionType.SET_SCORE) {
                val score = compareBase.get<LocationData>("score")
                var value = compareBase.get<Int>("value")
                while(index < instructions.size) {
                    val compare = instructions[index]
                    if(compareBase.type != compare.type) break
                    if(score != compare.get<LocationData>("score")) break
                    value = compare["value"]
                    index++
                }
                results.add(Instructions.setScore(score, value))
            }else {
                results.add(compareBase.copy(children = runOptimizations(compareBase.children).toMutableList()))
            }
            checkIndex = index
        }
        return results
    }

}