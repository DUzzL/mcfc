package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.il.InstructionType

private val instructionEmitters = mutableListOf<EmitterContext.() -> Boolean>()

fun registerEmitterInstruction(type: InstructionType, exec: EmitterContext.() -> Unit) {
    instructionEmitters.add {
        if(type != instruction.type) {
            return@add false
        }
        exec()
        return@add true
    }
}

fun registerEmitterInstruction(exec: java.util.function.Function<EmitterContext, Boolean>) {
    instructionEmitters.add {
        exec.apply(this)
    }
}

fun emitInstruction(context: EmitterContext) {
    for(emitter in instructionEmitters) {
        if(emitter(context)) {
            return
        }
    }
}
