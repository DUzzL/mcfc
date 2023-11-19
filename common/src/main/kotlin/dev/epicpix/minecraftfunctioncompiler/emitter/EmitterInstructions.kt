package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.il.InstructionType

private val instructionEmitters = mutableMapOf<InstructionType, EmitterContext.() -> Unit>()

fun registerEmitterInstruction(type: InstructionType, exec: EmitterContext.() -> Unit) {
    instructionEmitters[type] = exec
}

fun getInstructionEmitter(type: InstructionType) = instructionEmitters[type] ?: error("Instruction Emitter for $type not found!")
