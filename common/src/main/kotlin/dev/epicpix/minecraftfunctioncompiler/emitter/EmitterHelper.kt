package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument
import org.objectweb.asm.Opcodes

fun EmitterContext.checkFloatRange(range: FloatRangeArgument, exec: EmitterContext.() -> Unit) {
    if(range.minimum != null && range.maximum == null) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        val endLabel = codeWriter.executeIfLabeled(Opcodes.IF_ICMPNE)
        exec()
        codeWriter.methodVisitor.visitLabel(endLabel)
        return
    }else if(range.minimum == null && range.maximum != null) {
        codeWriter.writeFloat(range.maximum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.writeInt(1)
        val endLabel = codeWriter.executeIfLabeled(Opcodes.IF_ICMPNE)
        exec()
        codeWriter.methodVisitor.visitLabel(endLabel)
        return
    }else if(range.minimum != null && range.minimum == range.maximum) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        val endLabel = codeWriter.executeIfLabeled(Opcodes.IFNE)
        exec()
        codeWriter.methodVisitor.visitLabel(endLabel)
        return
    }
    val uid = codeWriter.generateUniqueLocal()
    codeWriter.storeLocal(uid, Float::class.javaPrimitiveType!!) {
        codeWriter.loadLocal(uid)
        codeWriter.writeFloat(range.minimum!!)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        val endLabel1 = codeWriter.executeIfLabeled(Opcodes.IF_ICMPNE)
        codeWriter.loadLocal(uid)
        codeWriter.writeFloat(range.maximum!!)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.writeInt(1)
        val endLabel2 = codeWriter.executeIfLabeled(Opcodes.IF_ICMPNE)
        exec()
        codeWriter.methodVisitor.visitLabel(endLabel2)
        codeWriter.methodVisitor.visitLabel(endLabel1)
    }
}