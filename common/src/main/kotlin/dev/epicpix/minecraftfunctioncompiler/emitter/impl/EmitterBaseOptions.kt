package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.emitter.registerEmitterInstruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import org.objectweb.asm.Opcodes

fun registerBaseInstructionEmitters() {
    registerEmitterInstruction(InstructionTypes.CHECK_LIMIT) {
        val scope = scopeId("scope")
        codeWriter.methodVisitor.visitIincInsn(codeWriter.getLocalIndex(newTransforms[scope]!!), -1)
        load("scope")
        codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scope]!!)
    }
}