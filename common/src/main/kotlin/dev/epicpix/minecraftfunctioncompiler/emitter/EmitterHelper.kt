package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument
import org.objectweb.asm.Opcodes

fun EmitterContext.checkFloatRange(range: FloatRangeArgument, exec: EmitterContext.() -> Unit) {
    if(range.minimum != null && range.maximum == null) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        val endIf = codeWriter.executeIf(Opcodes.IF_ICMPNE)
        exec()
        endIf()
    }else if(range.minimum == null && range.maximum != null) {
        codeWriter.writeFloat(range.maximum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.writeInt(1)
        val endIf = codeWriter.executeIf(Opcodes.IF_ICMPNE)
        exec()
        endIf()
    }else if(range.minimum != null && range.minimum == range.maximum) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        val endIf = codeWriter.executeIf(Opcodes.IFNE)
        exec()
        endIf()
    }else if(range.minimum != null) {
        val uid = codeWriter.generateUniqueLocal("\$Float")
        val end = codeWriter.storeLocal(uid, Float::class.javaPrimitiveType!!)
        codeWriter.loadLocal(uid)
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        val endIf = codeWriter.executeIf(Opcodes.IF_ICMPNE)
        codeWriter.loadLocal(uid)
        codeWriter.writeFloat(range.maximum!!)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.writeInt(1)
        val endIf2 = codeWriter.executeIf(Opcodes.IF_ICMPNE)
        exec()
        endIf2()
        endIf()
        end()
    }
}