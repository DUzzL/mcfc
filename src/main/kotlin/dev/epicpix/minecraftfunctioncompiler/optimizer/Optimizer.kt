package dev.epicpix.minecraftfunctioncompiler.optimizer

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.CommonSubexpressionEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.DeadCodeEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.InstructionMergeOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.LocalInvariantCodeHoistingOptimization

typealias InstructionList = List<Instruction>

interface Optimization {
    fun optimize(instructions: InstructionList): InstructionList
}

fun optimize(instructions: InstructionList): List<Instruction> {
    var newInstructions = instructions
    do {
        val previousInstructions = newInstructions
        newInstructions = DeadCodeEliminationOptimization.optimize(newInstructions)
        newInstructions = CommonSubexpressionEliminationOptimization.optimize(newInstructions)
        newInstructions = LocalInvariantCodeHoistingOptimization.optimize(newInstructions)
        newInstructions = InstructionMergeOptimization.optimize(newInstructions)
    } while(newInstructions != previousInstructions)
    return newInstructions
}
