package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations

import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.optimizer.InstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization

private data class DeadCodeEliminationData(
    val knownNonNulls: List<Int>,
    val unusedLoops: List<Int>,
)

object DeadCodeEliminationOptimization : Optimization {
    override fun optimize(instructions: InstructionList): InstructionList {
        return runOptimizations(instructions, DeadCodeEliminationData(listOf(), listOf()))
    }

    private fun runOptimizations(instructions: InstructionList, dce: DeadCodeEliminationData): InstructionList {
        val results = mutableListOf<Instruction>()
        for(it in instructions) {
            results.addAll(runOptimizations(it, dce))
            if(it.type == InstructionType.BREAK_LOOP || it.type == InstructionType.CONTINUE_LOOP) break
        }
        return results
    }

    private fun runOptimizations(instruction: Instruction, dce: DeadCodeEliminationData): InstructionList {
        var newDce = dce
        when(instruction.type) {
            InstructionType.IF_NOT_NULL -> {
                val data = instruction.data as InstructionDataLocation
                val local = data.local
                if(local.local != null) {
                    if(newDce.knownNonNulls.contains(local.local)) {
                        return runOptimizations(instruction.children, newDce)
                    }else {
                        newDce = newDce.copy(knownNonNulls = newDce.knownNonNulls.toMutableList().apply { add(local.local) })
                    }
                }else if(local.string != null) {
                    return runOptimizations(instruction.children, newDce)
                }
            }
            InstructionType.IF_NULL -> {
                val data = instruction.data as InstructionDataLocation
                val local = data.local
                if(local.local != null) {
                    if(newDce.knownNonNulls.contains(local.local)) {
                        return emptyList()
                    }
                }else if(local.string != null) {
                    return instruction.children
                }
            }
            InstructionType.EMPTY_CONDITION -> {
                val data = instruction.data as InstructionDataLoop
                val usage = getLoopUsed(instruction, data.loopId, true)
                if(!usage.usedBreak && usage.last) {
                    newDce = newDce.copy(unusedLoops = newDce.unusedLoops.toMutableList().apply { add(data.loopId) })
                    if(!usage.usedIndirect) {
                        return runOptimizations(instruction.children, newDce)
                    }
                }
            }
            InstructionType.BREAK_LOOP -> {
                val data = instruction.data as InstructionDataLoop
                if(newDce.unusedLoops.contains(data.loopId)) {
                    return emptyList()
                }
            }
            InstructionType.CONTINUE_LOOP -> {
                val data = instruction.data as InstructionDataLoop
                if(newDce.unusedLoops.contains(data.loopId)) {
                    return emptyList()
                }
            }
            else -> {}
        }
        return listOf(Instruction(instruction.type, instruction.data, runOptimizations(instruction.children, newDce).toMutableList()))
    }
}

private data class LoopUsageInfo(val usedBreak: Boolean, val usedIndirect: Boolean, val last: Boolean)

private fun getLoopUsed(instruction: Instruction, loopId: Int, lastElement: Boolean = false, isInLoop: Boolean = false): LoopUsageInfo {
    var usedBreak = false
    var usedIndirect = false
    var wLoop = isInLoop
    when(instruction.type) {
        InstructionType.LIMITER -> wLoop = true
        InstructionType.FOR_EACH_ENTITY -> wLoop = true
        InstructionType.FOR_EACH_PLAYER -> wLoop = true
        InstructionType.FOR_SCORE_HOLDERS -> wLoop = true
        InstructionType.CHECK_ENTITY_TAG, InstructionType.CHECK_NOT_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationStringLoop
            if(data.loopId == loopId) {
                return LoopUsageInfo(usedBreak = false, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.CHECK_ENTITY_TAGS, InstructionType.CHECK_NOT_ENTITY_TAGS -> {
            val data = instruction.data as InstructionDataLocationLoop
            if(data.loopId == loopId) {
                return LoopUsageInfo(usedBreak = false, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.CHECK_LIMIT -> {
            val data = instruction.data as InstructionDataLoop
            if(data.loopId == loopId) {
                return LoopUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.RUN_SCORE_OPERATION -> {
            val data = instruction.data as InstructionDataRunScoreOperation
            if(data.exceptionLoopId == loopId && (data.operation == ScoreboardOperation.DIVIDE || data.operation == ScoreboardOperation.MODULO)) {
                return LoopUsageInfo(usedBreak = true, usedIndirect = true, last = lastElement)
            }
        }
        InstructionType.BREAK_LOOP, InstructionType.CONTINUE_LOOP -> {
            val data = instruction.data as InstructionDataLoop
            if(data.loopId == loopId && (!lastElement || wLoop)) {
                return LoopUsageInfo(usedBreak = true, usedIndirect = false, last = lastElement)
            }
        }
        else -> {}
    }
    var usedLastElement = lastElement
    instruction.children.forEachIndexed { index, instr ->
        val info = getLoopUsed(instr, loopId, index == instruction.children.size - 1, wLoop)
        usedBreak = usedBreak or info.usedBreak
        usedIndirect = usedIndirect or info.usedIndirect
        usedLastElement = usedLastElement and (index == instruction.children.size - 1)
    }
    return LoopUsageInfo(usedBreak, usedIndirect, usedLastElement)
}