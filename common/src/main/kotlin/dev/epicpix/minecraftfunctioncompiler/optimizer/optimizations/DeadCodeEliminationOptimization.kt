package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

private data class DeadCodeEliminationData(
    val knownNonNulls: List<Int>,
    val unusedScopes: List<Int>,
)

object DeadCodeEliminationOptimization : Optimization {
    override fun optimize(instructions: List<Instruction>): List<Instruction> {
        return runOptimizations(instructions, DeadCodeEliminationData(listOf(), listOf()))
    }

    private fun runOptimizations(instructions: List<Instruction>, dce: DeadCodeEliminationData): List<Instruction> {
        val results = mutableListOf<Instruction>()
        for(it in instructions) {
            results.addAll(runOptimizations(it, dce))
            if(it.type == InstructionTypes2.EXIT_SCOPE) break
        }
        return results
    }

    private fun runOptimizations(instruction: Instruction, dce: DeadCodeEliminationData): List<Instruction> {
        var newDce = dce
        when(instruction.type) {
            InstructionTypes2.IF_NOT_NULL -> {
                val local = instruction.get<LocationData<Any>>("local")
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
            InstructionTypes2.SCOPE -> {
                val scope = instruction.get<ScopeData>("scope").id
                val usage = getScopeUsed(instruction, scope, true)
                if(!usage.usedBreak && usage.last) {
                    newDce = newDce.copy(unusedScopes = newDce.unusedScopes.toMutableList().apply { add(scope) })
                    if(!usage.usedIndirect) {
                        return runOptimizations(instruction.children, newDce)
                    }
                }
            }
            InstructionTypes2.EXIT_SCOPE -> {
                val scope = instruction.get<ScopeData>("scope").id
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
        InstructionTypes2.LIMITER -> wLoop = true
        InstructionTypes2.FOR_EACH_ENTITY -> wLoop = true
        InstructionTypes2.FOR_EACH_PLAYER -> wLoop = true
        InstructionTypes2.FOR_MULTIPLE_SCORE_HOLDERS -> wLoop = true
        InstructionTypes2.CHECK_LIMIT -> {
            val scope = instruction.get<ScopeData>("scope").id
            if(scope == inScopeId) {
                return ScopeUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionTypes2.RUN_SCORE_OPERATION_DIVIDE, InstructionTypes2.RUN_SCORE_OPERATION_MODULO -> {
            val exceptionScope = instruction.get<ScopeData>("exceptionScope").id
            if(exceptionScope == inScopeId) {
                return ScopeUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionTypes2.EXIT_SCOPE -> {
            val scope = instruction.get<ScopeData>("scope").id
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