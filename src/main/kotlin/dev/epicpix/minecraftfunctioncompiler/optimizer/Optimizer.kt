package dev.epicpix.minecraftfunctioncompiler.optimizer

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.*

typealias InstructionList = List<Instruction>

interface Optimization {
    fun optimize(instructions: InstructionList): InstructionList
}

val optimizations: List<Optimization> = listOf(
    DeadCodeEliminationOptimization,
    CommonSubexpressionEliminationOptimization,
    LocalInvariantCodeHoistingOptimization,
    ScopeInstructionMergeOptimization,
    InstructionMergeOptimization,
)

fun optimize(instructions: InstructionList): List<Instruction> {
    var newInstructions = instructions
    do {
        val previousInstructions = newInstructions
        optimizations.forEach { newInstructions = it.optimize(newInstructions) }
    } while(newInstructions != previousInstructions)
    return newInstructions
}
