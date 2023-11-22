package dev.epicpix.minecraftfunctioncompiler.emitter

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

interface ICodeWriter {
    val methodVisitor: MethodVisitor

    fun getLocalIndex(local: String): Int
    fun defineLocal(local: String, type: Class<*>): () -> Unit
    fun endLabels()
    fun writeLocalVariables()
    fun loadLocal(local: String)
    fun storeLocal(local: String)
    fun writeInt(value: Int) {
        if (value >= -1 && value <= 5) {
            methodVisitor.visitInsn(Opcodes.ICONST_0 + value)
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, value)
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, value)
        } else {
            methodVisitor.visitLdcInsn(value)
        }
    }
    fun writeLong(value: Long) = methodVisitor.visitLdcInsn(value)
    fun writeFloat(value: Float) = methodVisitor.visitLdcInsn(value)
    fun writeDouble(value: Double) = methodVisitor.visitLdcInsn(value)
    fun writeString(value: String) = methodVisitor.visitLdcInsn(value)
    fun writeNull() = methodVisitor.visitInsn(Opcodes.ACONST_NULL)
    fun newObject(owner: Class<*>, vararg types: Class<*>): () -> Unit
    fun writeMapped(mappedData: MappedFieldData?, load: Boolean)
    fun writeMapped(mappedData: MappedFunctionData?)
    fun getDataField(name: String, type: Class<*>)
    fun setDataField(name: String, type: Class<*>)
    fun castType(type: Class<*>)
    fun getIfJumpOpcode(comparison: Int): Int
    fun executeIf(comparison: Int): () -> Unit
    fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowExecution) -> Unit)
    fun createIndexedLoop(exec: (indexLocal: String) -> Unit)
    fun createListForEachLoop(arrayLocal: String, type: Class<*>, exec: () -> Unit)
    fun createIteratorForEachLoop(iteratorLocal: String, type: Class<*>, autoLoad: Boolean = false, exec: (getter: () -> Unit) -> Unit)
    fun createIterableForEachLoop(iterableLocal: String, type: Class<*>, autoLoad: Boolean = false, exec: (getter: () -> Unit) -> Unit)
    fun generateUniqueLocal(local: String): String

    fun storeLocal(local: String, descriptor: Class<*>): () -> Unit {
        val end = defineLocal(local, descriptor)
        storeLocal(local)
        return end
    }
}