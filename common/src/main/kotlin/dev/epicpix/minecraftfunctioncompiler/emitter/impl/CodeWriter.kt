package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.DATA_CLASS
import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.emitter.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

private data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)

class CodeWriter(override val methodVisitor: MethodVisitor) : ICodeWriter {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

    override fun getLocalIndex(local: String) = locals[local]!!.index

    override fun defineLocal(local: String, type: Class<*>): () -> Unit {
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

    override fun endLabels() {
        val endLabel = Label()
        methodVisitor.visitLabel(endLabel)
        generatedLocals.forEach {
            if(it.end == null) {
                it.end = endLabel
            }
        }
    }

    override fun writeLocalVariables() {
        if(DEBUG) {
            generatedLocals.forEach {
                methodVisitor.visitLocalVariable(it.name.replace(".", "$").replace(":", "$"), it.descriptor, null, it.start, it.end, it.index)
            }
        }
    }

    override fun loadLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor == "I") {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index)
        }
    }

    override fun storeLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor == "I") {
            methodVisitor.visitVarInsn(Opcodes.ISTORE, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar.index)
        }
    }

    override fun newObject(owner: Class<*>, vararg types: Class<*>): () -> Unit {
        val ownerName = owner.descriptorString().run { substring(1, length - 1) }
        methodVisitor.visitTypeInsn(Opcodes.NEW, ownerName)
        methodVisitor.visitInsn(Opcodes.DUP)
        return {
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, ownerName, "<init>", "(${types.joinToString("") { it.descriptorString() }})V", false)
        }
    }

    override fun writeMapped(mappedData: MappedFieldData?, load: Boolean) {
        if(mappedData == null) error("Tried to use an uninitialized MappedFieldData (not supported in this minecraft version?)")
        methodVisitor.visitFieldInsn(mappedData.primaryOpcode + (if(load) 0 else 1), mappedData.clazz, mappedData.fieldName, mappedData.fieldType)
    }

    override fun writeMapped(mappedData: MappedFunctionData?) {
        if(mappedData == null) error("Tried to use an uninitialized MappedFunctionData (not supported in this minecraft version?)")
        methodVisitor.visitMethodInsn(mappedData.opcode, mappedData.clazz, mappedData.methodName, mappedData.methodDescriptor, mappedData.isInterface)
    }

    override fun getDataField(name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, name, type.descriptorString())
    }

    override fun setDataField(name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, name, type.descriptorString())
    }

    override fun castType(type: Class<*>) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, type.descriptorString().run { substring(1, length - 1) })
    }

    override fun getIfJumpOpcode(comparison: Int): Int {
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

    override fun executeIf(comparison: Int): () -> Unit {
        val endLabel = Label()
        methodVisitor.visitJumpInsn(getIfJumpOpcode(comparison), endLabel)
        return { methodVisitor.visitLabel(endLabel) }
    }

    override fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowExecution) -> Unit) {
        val beginLabel = Label()
        val endLabel = Label()
        methodVisitor.visitLabel(beginLabel)
        condition(endLabel)
        exec(ControlFlowExecution(beginLabel, endLabel))
        methodVisitor.visitLabel(endLabel)
    }

    override fun createIndexedLoop(exec: (indexLocal: String) -> Unit) {
        val baseName = generateUniqueLocal("\$Indexer")
        val sizeName = "$baseName:Size"
        val indexName = "$baseName:Index"
        val endSize = storeLocal(sizeName, Int::class.javaPrimitiveType!!)
        writeInt(0)
        val endIndex = storeLocal(indexName, Int::class.javaPrimitiveType!!)
        createWhileLoop({ brk ->
            loadLocal(indexName)
            loadLocal(sizeName)
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, brk)
        }) { controlFlow ->
            exec(indexName)
            methodVisitor.visitIincInsn(locals[indexName]!!.index, 1)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, controlFlow.continueJump)
        }
        endIndex()
        endSize()
    }

    override fun createListForEachLoop(arrayLocal: String, type: Class<*>, exec: () -> Unit) {
        loadLocal(arrayLocal)
        writeMapped(JavaMappedUsageDefinitions.List_size)
        createIndexedLoop { indexLocal ->
            loadLocal(arrayLocal)
            loadLocal(indexLocal)
            writeMapped(JavaMappedUsageDefinitions.List_get)
            castType(type)
            exec()
        }
    }

    override fun createIteratorForEachLoop(iteratorLocal: String, type: Class<*>, autoLoad: Boolean, exec: (getter: () -> Unit) -> Unit) {
        createWhileLoop({ brk ->
            loadLocal(iteratorLocal)
            writeMapped(JavaMappedUsageDefinitions.Iterator_hasNext)
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, brk)
        }) { controlFlow ->
            loadLocal(iteratorLocal)
            writeMapped(JavaMappedUsageDefinitions.Iterator_next)
            if(type != Object::class.java) castType(type)

            if(!autoLoad) {
                val end = defineLocal("$iteratorLocal:Element", type)
                storeLocal("$iteratorLocal:Element")

                exec { loadLocal("$iteratorLocal:Element") }
                end()
            }else {
                exec { throw UnsupportedOperationException("Cannot run getter with autoLoad enabled") }
            }

            methodVisitor.visitJumpInsn(Opcodes.GOTO, controlFlow.continueJump)
        }
    }

    override fun createIterableForEachLoop(iterableLocal: String, type: Class<*>, autoLoad: Boolean, exec: (getter: () -> Unit) -> Unit) {
        loadLocal(iterableLocal)
        writeMapped(JavaMappedUsageDefinitions.Iterable_iterator)
        val endIterator = storeLocal("$iterableLocal:Iterator", Iterator::class.java)
        createIteratorForEachLoop("$iterableLocal:Iterator", type, autoLoad, exec)
        endIterator()
    }

    override fun generateUniqueLocal(local: String): String {
        return "$local:${generatedLocals.size}"
    }

}