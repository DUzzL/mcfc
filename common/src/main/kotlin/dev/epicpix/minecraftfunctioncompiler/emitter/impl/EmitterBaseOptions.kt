package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.emitter.EmitControlFlow
import dev.epicpix.minecraftfunctioncompiler.emitter.registerEmitterInstruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun registerBaseInstructionEmitters() {
    registerEmitterInstruction(InstructionTypes.SCOPE) {
        val label = Label()
        newScopes[scopeId("scope")] = EmitControlFlow(label, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }

    registerEmitterInstruction(InstructionTypes.EXIT_SCOPE) {
        codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newScopes[scopeId("scope")]!!.breakLabel)
    }

    registerEmitterInstruction(InstructionTypes.LIMITER) {
        codeWriter.writeInt(instruction["limit"])
        val label = Label()
        storeCreate("scope", codeWriter.generateUniqueLocal("\$LimitRemaining"), Int::class.javaPrimitiveType!!) {
            newScopes[scopeId("scope")] = EmitControlFlow(label, label)
            run()
        }
        codeWriter.methodVisitor.visitLabel(label)
    }

    registerEmitterInstruction(InstructionTypes.CHECK_LIMIT) {
        val scope = scopeId("scope")
        codeWriter.methodVisitor.visitIincInsn(codeWriter.getLocalIndex(newTransforms[scope]!!), -1)
        load("scope")
        codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scope]!!.breakLabel)
    }

    registerEmitterInstruction(InstructionTypes.CREATE_RESULT_STORAGE) {
        codeWriter.writeInt(0)
        runStored("storage", "\$ResultStorage", Int::class.javaPrimitiveType!!)
    }

    registerEmitterInstruction(InstructionTypes.ADD_RESULT_STORAGE) {
        load("storage")
        load("value")
        codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
        save("storage")
    }
}