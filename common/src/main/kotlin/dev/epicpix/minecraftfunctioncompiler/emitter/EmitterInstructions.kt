package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import java.util.function.Consumer

private val instructionEmitters = mutableMapOf<InstructionType, EmitterContext.() -> Unit>()

fun registerEmitterInstruction(type: InstructionType, exec: EmitterContext.() -> Unit) {
    instructionEmitters[type] = exec
}

fun registerEmitterInstruction(type: InstructionType, exec: Consumer<EmitterContext>) {
    instructionEmitters[type] = { exec.accept(this) }
}

fun getInstructionEmitter(type: InstructionType) = instructionEmitters[type] ?: error("Instruction Emitter for $type not found!")
