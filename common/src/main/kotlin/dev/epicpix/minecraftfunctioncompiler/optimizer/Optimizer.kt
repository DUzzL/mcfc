package dev.epicpix.minecraftfunctioncompiler.optimizer

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.CommonSubexpressionEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.DeadCodeEliminationOptimization
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.ScopeInstructionMergeOptimization

typealias InstructionList = List<Instruction>

val optimizations: List<Optimization> = listOf(
    DeadCodeEliminationOptimization,
    CommonSubexpressionEliminationOptimization,
//    LocalInvariantCodeHoistingOptimization,
    ScopeInstructionMergeOptimization,
)

fun optimize(instructions: InstructionList, statistics: MutableList<OptimizationStatistics>): InstructionList {
    var newInstructions = instructions
    statistics.add(OptimizationStatistics("start", instructions.sumOf { it.countInstructions() }, 0.0))
    val start = System.nanoTime()
    do {
        val previousInstructions = newInstructions
        val startOptim = System.nanoTime()
        optimizations.forEach { newInstructions = it.optimize(newInstructions) }
        val endOptim = System.nanoTime()
        statistics.add(OptimizationStatistics("optimization_pass", newInstructions.sumOf { it.countInstructions() }, (endOptim - startOptim)/1000000000.0))
    } while(newInstructions != previousInstructions)
    val end = System.nanoTime()
    statistics.add(OptimizationStatistics("end", newInstructions.sumOf { it.countInstructions() }, (end - start)/1000000000.0))
    return newInstructions
}
