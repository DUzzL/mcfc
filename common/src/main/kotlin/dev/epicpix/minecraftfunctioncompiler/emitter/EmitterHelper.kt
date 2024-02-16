package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument
import org.objectweb.asm.Opcodes

fun EmitterContext.checkFloatRange(range: FloatRangeArgument, exec: EmitterContext.() -> Unit) {
    if(range.minimum != null && range.maximum == null) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        codeWriter.executeIf(Opcodes.IF_ICMPNE) {
            exec()
        }
        return
    }else if(range.minimum == null && range.maximum != null) {
        codeWriter.writeFloat(range.maximum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.writeInt(1)
        codeWriter.executeIf(Opcodes.IF_ICMPNE) {
            exec()
        }
        return
    }else if(range.minimum != null && range.minimum == range.maximum) {
        codeWriter.writeFloat(range.minimum)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
        codeWriter.executeIf(Opcodes.IFNE) {
            exec()
        }
        return
    }
    val uid = codeWriter.generateUniqueLocal("\$Float")
    codeWriter.storeLocal(uid, Float::class.javaPrimitiveType!!) {
        codeWriter.loadLocal(uid)
        codeWriter.writeFloat(range.minimum!!)
        codeWriter.methodVisitor.visitInsn(Opcodes.FCMPL)
        codeWriter.writeInt(-1)
        codeWriter.executeIf(Opcodes.IF_ICMPNE) {
            codeWriter.loadLocal(uid)
            codeWriter.writeFloat(range.maximum!!)
            codeWriter.methodVisitor.visitInsn(Opcodes.FCMPG)
            codeWriter.writeInt(1)
            codeWriter.executeIf(Opcodes.IF_ICMPNE) {
                exec()
            }
        }
    }
}