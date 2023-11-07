package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Iterable_iterator
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Iterator_hasNext
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Iterator_next
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.List_get
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.List_size
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1

class CodeWriter(val methodVisitor: MethodVisitor) {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

    fun getLocalIndex(local: String) = locals[local]!!.index

    fun defineLocal(local: String, type: Class<*>): () -> Unit {
        val localStart = Label()
        val localEnd = Label()
        methodVisitor.visitLabel(localStart)

        val localIndex: Int
        if(availableLocals.isEmpty()) {
            localIndex = locals.size
        }else {
            localIndex = availableLocals.first()
            availableLocals.remove(localIndex)
        }
        locals[local] = Local(local, type.descriptorString(), localStart, localEnd, localIndex)
        generatedLocals.add(locals[local]!!)

        return {
            methodVisitor.visitLabel(localEnd)

            locals.remove(local)
            availableLocals.add(localIndex)
        }
    }

    private fun storeLocal(local: String, descriptor: Class<*>, exec: (CodeWriter.() -> Unit)) {
        val end = defineLocal(local, descriptor)
        storeLocal(local)
        exec()
        end()
    }

    fun end() {
        val endLabel = Label()
        methodVisitor.visitLabel(endLabel)
        generatedLocals.forEach {
            if(it.end == null) {
                it.end = endLabel
            }
        }
    }

    fun writeLocalVariables() {
        if(DEBUG) {
            generatedLocals.forEach {
                methodVisitor.visitLocalVariable(it.name.replace(".", "$").replace(":", "$"), it.descriptor, null, it.start, it.end, it.index)
            }
        }
    }

    fun loadLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor == Int::class.javaPrimitiveType!!.descriptorString()) {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index)
        }
    }

    fun storeLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor == Int::class.javaPrimitiveType!!.descriptorString()) {
            methodVisitor.visitVarInsn(Opcodes.ISTORE, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar.index)
        }
    }

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

    fun writeLong(value: Long) {
        methodVisitor.visitLdcInsn(value)
    }

    fun writeString(str: String) {
        methodVisitor.visitLdcInsn(str)
    }

    fun writeNull() {
        methodVisitor.visitInsn(Opcodes.ACONST_NULL)
    }

    fun newObject(owner: Class<*>, vararg types: Class<*>, argumentProvider: CodeWriter.() -> Unit) {
        methodVisitor.visitTypeInsn(Opcodes.NEW, owner.getRawDescriptor())
        methodVisitor.visitInsn(Opcodes.DUP)
        argumentProvider()
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner.getRawDescriptor(), "<init>", types.getMethodDescriptor(Void::class.javaPrimitiveType!!), false)
    }

    fun callFunction(owner: Class<*>, name: String, returnType: Class<*>, vararg arguments: Class<*>) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.getRawDescriptor(), name, arguments.getMethodDescriptor(returnType), false)
    }

    fun callMethod(function: KFunction<*>) {
        function as CallableReference
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            (function.owner as KClass<*>).java.getRawDescriptor(),
            function.signature.substring(0, function.signature.indexOf('(')),
            function.signature.substring(function.signature.indexOf('(')),
            false
        )
    }

    fun callMethod(function: KProperty1<*, *>) {
        function as CallableReference
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            (function.owner as KClass<*>).java.getRawDescriptor(),
            function.signature.substring(0, function.signature.indexOf('(')),
            function.signature.substring(function.signature.indexOf('(')),
            false
        )
    }

    fun getObjectField(property: KProperty1<*, *>) {
        property as CallableReference
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, (property.owner as KClass<*>).java.getRawDescriptor(), property.name, (property.returnType.classifier as KClass<*>).java.descriptorString())
    }

    fun writeMapped(mappedData: MappedData) {
        mappedData.generate(this)
    }

    fun getStaticField(owner: String, name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, owner, name, type.descriptorString())
    }

    fun setStaticField(owner: String, name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, type.descriptorString())
    }

    fun castType(type: Class<*>) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, type.getRawDescriptor())
    }

    fun getIfJumpOpcode(comparison: Int): Int {
        return when (comparison) {
            Opcodes.IFNONNULL -> Opcodes.IFNULL
            Opcodes.IFNULL -> Opcodes.IFNONNULL
            Opcodes.IFNE -> Opcodes.IFNE
            Opcodes.IFEQ -> Opcodes.IFEQ
            Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE
            Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
            Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
            Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE
            Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
            Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE
            else -> throw UnsupportedOperationException("Unknown comparison opcode: $comparison")
        }
    }

    fun executeIf(comparison: Int, exec: () -> Unit) {
        val endLabel = Label()
        methodVisitor.visitJumpInsn(getIfJumpOpcode(comparison), endLabel)
        exec()
        methodVisitor.visitLabel(endLabel)
    }

    fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowRun) -> Unit) {
        val beginLabel = Label()
        val endLabel = Label()
        methodVisitor.visitLabel(beginLabel)
        condition(endLabel)
        exec(ControlFlowRun(this, beginLabel, endLabel))
        methodVisitor.visitLabel(endLabel)
    }

    fun createIndexedLoop(exec: (indexLocal: String, ControlFlowRun) -> Unit) {
        val baseName = generateUniqueLocal("\$Indexer")
        val sizeName = "$baseName:Size"
        val indexName = "$baseName:Index"
        storeLocal(sizeName, Int::class.javaPrimitiveType!!) {

            writeInt(0)
            storeLocal(indexName, Int::class.javaPrimitiveType!!) {

                createWhileLoop({ brk ->
                    loadLocal(indexName)
                    loadLocal(sizeName)
                    methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, brk)
                }) { controlFlow ->
                    val contLabel = Label()
                    exec(indexName, ControlFlowRun(this, contLabel, controlFlow.breakJump))
                    methodVisitor.visitLabel(contLabel)
                    methodVisitor.visitIincInsn(locals[indexName]!!.index, 1)
                    controlFlow.cont()
                }
            }
        }
    }

    fun createListForEachLoop(arrayLocal: String, type: Class<*>, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        loadLocal(arrayLocal)
        writeMapped(List_size)
        createIndexedLoop { indexLocal, controlFlow ->
            if(autoLoad) {
                loadLocal(arrayLocal)
                loadLocal(indexLocal)
                writeMapped(List_get)
                castType(type)
                exec(controlFlow) { throw UnsupportedOperationException("Cannot run getter with autoLoad enabled") }
            }else {
                exec(controlFlow) {
                    loadLocal(arrayLocal)
                    loadLocal(indexLocal)
                    writeMapped(List_get)
                    castType(type)
                }
            }
        }
    }

    fun createIteratorForEachLoop(iteratorLocal: String, type: Class<*>, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        createWhileLoop({ brk ->
            loadLocal(iteratorLocal)
            writeMapped(Iterator_hasNext)
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, brk)
        }) { controlFlow ->
            loadLocal(iteratorLocal)
            writeMapped(Iterator_next)
            if(type != Object::class.java) castType(type)

            if(!autoLoad) {
                val end = defineLocal("$iteratorLocal:Element", type)
                storeLocal("$iteratorLocal:Element")

                exec(controlFlow) { loadLocal("$iteratorLocal:Element") }
                end()
            }else {
                exec(controlFlow) { throw UnsupportedOperationException("Cannot run getter with autoLoad enabled") }
            }

            controlFlow.cont()
        }
    }

    fun createIterableForEachLoop(iterableLocal: String, type: Class<*>, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        loadLocal(iterableLocal)
        writeMapped(Iterable_iterator)
        storeLocal("$iterableLocal:Iterator", Iterator::class.java) {
            createIteratorForEachLoop("$iterableLocal:Iterator", type, autoLoad, exec)
        }
    }

    fun generateUniqueLocal(local: String): String {
        return "$local:${generatedLocals.size}"
    }

    internal fun Class<*>.getRawDescriptor(): String {
        val descriptor = descriptorString()
        return descriptor.substring(1, descriptor.length - 1)
    }

    internal fun Array<out Class<*>>.getMethodDescriptor(returnType: Class<*>): String {
        var res = "("
        for(clz in this) {
            res += clz.descriptorString()
        }
        res += ")"
        return res + returnType.descriptorString()
    }

}

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)
data class ControlFlowRun(val codeWriter: CodeWriter, val continueJump: Label, val breakJump: Label) {
    fun cont() = codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, continueJump)
    fun cont(condition: Int) = codeWriter.methodVisitor.visitJumpInsn(condition, continueJump)

    fun brk() = codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, breakJump)
    fun brk(condition: Int) = codeWriter.methodVisitor.visitJumpInsn(condition, breakJump)
}