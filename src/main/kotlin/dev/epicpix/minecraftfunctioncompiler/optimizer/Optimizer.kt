package dev.epicpix.minecraftfunctioncompiler.optimizer

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.CommonSubexpressionEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.DeadCodeEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.LocalInvariantCodeHoistingOptimization

typealias InstructionList = List<Instruction>

interface Optimization {
    fun optimize(instructions: InstructionList): InstructionList
}

private val optimizations: List<Optimization> = listOf(
    DeadCodeEliminationOptimization,
    CommonSubexpressionEliminationOptimization,
    LocalInvariantCodeHoistingOptimization,
)

fun optimize(instructions: InstructionList): List<Instruction> {
    var newInstructions = instructions
    do {
        val previousInstructions = newInstructions
        optimizations.forEach { optimization ->
            newInstructions = optimization.optimize(newInstructions)
        }
    } while(newInstructions != previousInstructions)
    return newInstructions
}
