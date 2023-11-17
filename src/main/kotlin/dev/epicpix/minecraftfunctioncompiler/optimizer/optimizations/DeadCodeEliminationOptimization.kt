package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardOperation
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

private data class DeadCodeEliminationData(
    val knownNonNulls: List<Int>,
    val unusedScopes: List<Int>,
)

object DeadCodeEliminationOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions, DeadCodeEliminationData(listOf(), listOf()))
    }

    private fun runOptimizations(instructions: InstructionList, dce: DeadCodeEliminationData): InstructionList {
        val results = mutableListOf<Instruction>()
        for(it in instructions) {
            results.addAll(runOptimizations(it, dce))
            if(it.type == InstructionType.EXIT_SCOPE) break
        }
        return results
    }

    private fun runOptimizations(instruction: Instruction, dce: DeadCodeEliminationData): InstructionList {
        var newDce = dce
        when(instruction.type) {
            InstructionType.IF_NOT_NULL -> {
                val local = instruction.get<LocationData>("local")
                if(local.local != null) {
                    if(newDce.knownNonNulls.contains(local.local)) {
                        return runOptimizations(instruction.children, newDce)
                    }else {
                        newDce = newDce.copy(knownNonNulls = newDce.knownNonNulls.toMutableList().apply { add(local.local) })
                    }
                }else {
                    return runOptimizations(instruction.children, newDce)
                }
            }
            InstructionType.SCOPE -> {
                val scope = instruction.get<Int>("scope")
                val usage = getScopeUsed(instruction, scope, true)
                if(!usage.usedBreak && usage.last) {
                    newDce = newDce.copy(unusedScopes = newDce.unusedScopes.toMutableList().apply { add(scope) })
                    if(!usage.usedIndirect) {
                        return runOptimizations(instruction.children, newDce)
                    }
                }
            }
            InstructionType.EXIT_SCOPE -> {
                val scope = instruction.get<Int>("scope")
                if(newDce.unusedScopes.contains(scope)) {
                    return emptyList()
                }
            }
            else -> {}
        }
        return listOf(Instruction(instruction.type, instruction.data, runOptimizations(instruction.children, newDce).toMutableList()))
    }
}

private data class ScopeUsageInfo(val usedBreak: Boolean, val usedIndirect: Boolean, val last: Boolean)

private fun getScopeUsed(instruction: Instruction, inScopeId: Int, lastElement: Boolean = false, isInLoop: Boolean = false): ScopeUsageInfo {
    var usedBreak = false
    var usedIndirect = false
    var wLoop = isInLoop
    when(instruction.type) {
        InstructionType.LIMITER -> wLoop = true
        InstructionType.FOR_EACH_ENTITY -> wLoop = true
        InstructionType.FOR_EACH_PLAYER -> wLoop = true
        InstructionType.FOR_SCORE_HOLDERS -> wLoop = true
        InstructionType.CHECK_LIMIT -> {
            val scope = instruction.get<Int>("scope")
            if(scope == inScopeId) {
                return ScopeUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.RUN_SCORE_OPERATION -> {
            val exceptionScope = instruction.get<Int>("exceptionScope")
            val operation = instruction.get<ScoreboardOperation>("operation")
            if(exceptionScope == inScopeId && (operation == ScoreboardOperation.DIVIDE || operation == ScoreboardOperation.MODULO)) {
                return ScopeUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.EXIT_SCOPE -> {
            val scope = instruction.get<Int>("scope")
            if(scope == inScopeId && (!lastElement || wLoop)) {
                return ScopeUsageInfo(usedBreak = true, usedIndirect = false, last = lastElement)
            }
        }
        else -> {}
    }
    instruction.children.forEachIndexed { index, instr ->
        val info = getScopeUsed(instr, inScopeId, lastElement && index == instruction.children.size - 1, wLoop)
        usedBreak = usedBreak or info.usedBreak
        usedIndirect = usedIndirect or info.usedIndirect
    }
    return ScopeUsageInfo(usedBreak, usedIndirect, lastElement)
}