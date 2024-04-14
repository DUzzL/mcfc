package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.DATA_CLASS
import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.emitter.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label, val index: Int)

class CodeWriter(val methodVisitor: MethodVisitor) {

    val locals: MutableMap<String, Local> = mutableMapOf()
    val availableLocals: MutableSet<Int> = mutableSetOf()
    val generatedLocals: MutableList<Local> = mutableListOf()

    fun getLocalIndex(local: String) = locals[local]!!.index

    fun allocateLocalIndex(): Int {
        val localIndex: Int
        if(availableLocals.isEmpty()) {
            localIndex = locals.size
        }else {
            localIndex = availableLocals.first()
            availableLocals.remove(localIndex)
        }
        return localIndex
    }

    fun deallocateLocalIndex(index: Int) {
        availableLocals.add(index)
    }

    fun createLocal(local: String, type: String): Local {
        val localStart = Label()
        methodVisitor.visitLabel(localStart)
        val localValue = Local(local, type, localStart, Label(), allocateLocalIndex())
        locals[local] = localValue
        generatedLocals.add(localValue)
        return localValue
    }

    fun destroyLocal(local: Local) {
        methodVisitor.visitLabel(local.end)
        locals.remove(local.name)
        deallocateLocalIndex(local.index)
    }

    inline fun defineLocal(local: String, type: Class<*>, exec: () -> Unit) {
        val localValue = createLocal(local, type.descriptorString())
        exec()
        destroyLocal(localValue)
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
        when(localVar.descriptor) {
            "I" -> methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index)
            "F" -> methodVisitor.visitVarInsn(Opcodes.FLOAD, localVar.index)
            else -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index)
        }
    }

    fun storeLocal(local: String) {
        val localVar = locals[local]!!
        when(localVar.descriptor) {
            "I" -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVar.index)
            "F" -> methodVisitor.visitVarInsn(Opcodes.FSTORE, localVar.index)
            else -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar.index)
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

    fun writeLong(value: Long) = methodVisitor.visitLdcInsn(value)
    fun writeFloat(value: Float) = methodVisitor.visitLdcInsn(value)
    fun writeDouble(value: Double) = methodVisitor.visitLdcInsn(value)
    fun writeString(value: String) = methodVisitor.visitLdcInsn(value)
    fun writeNull() = methodVisitor.visitInsn(Opcodes.ACONST_NULL)

    fun newObject(owner: Class<*>, vararg types: Class<*>, arguments: () -> Unit) {
        val ownerName = owner.descriptorString().run { substring(1, length - 1) }
        methodVisitor.visitTypeInsn(Opcodes.NEW, ownerName)
        methodVisitor.visitInsn(Opcodes.DUP)
        arguments()
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, ownerName, "<init>", "(${types.joinToString("") { it.descriptorString() }})V", false)
    }

    fun getDataField(name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, name, type.descriptorString())
    }

    fun setDataField(name: String, type: Class<*>) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, name, type.descriptorString())
    }

    fun castType(type: Class<*>) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, type.descriptorString().run { substring(1, length - 1) })
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

    inline fun executeIf(comparison: Int, exec: () -> Unit) {
        val endLabel = Label()
        methodVisitor.visitJumpInsn(getIfJumpOpcode(comparison), endLabel)
        exec()
        methodVisitor.visitLabel(endLabel)
    }

    inline fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowExecution) -> Unit) {
        val beginLabel = Label()
        val endLabel = Label()
        methodVisitor.visitLabel(beginLabel)
        condition(endLabel)
        exec(ControlFlowExecution(beginLabel, endLabel))
        methodVisitor.visitLabel(endLabel)
    }

    inline fun createIndexedLoop(exec: (indexLocal: String) -> Unit) {
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
                    exec(indexName)
                    methodVisitor.visitIincInsn(getLocalIndex(indexName), 1)
                    methodVisitor.visitJumpInsn(Opcodes.GOTO, controlFlow.continueJump)
                }
            }
        }
    }

    inline fun createListForEachLoop(arrayLocal: String, type: Class<*>, exec: () -> Unit) {
        callList_size { loadLocal(arrayLocal) }
        createIndexedLoop { indexLocal ->
            callList_get {
                loadLocal(arrayLocal)
                loadLocal(indexLocal)
            }
            castType(type)
            exec()
        }
    }

    inline fun createIteratorForEachLoop(iteratorLocal: String, type: Class<*>, autoLoad: Boolean, exec: (getter: () -> Unit) -> Unit) {
        createWhileLoop({ brk ->
            callIterator_hasNext { loadLocal(iteratorLocal) }
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, brk)
        }) { controlFlow ->
            callIterator_next { loadLocal(iteratorLocal) }
            if(type != Object::class.java) castType(type)

            if(!autoLoad) {
                storeLocal("$iteratorLocal:Element", type) {
                    exec { loadLocal("$iteratorLocal:Element") }
                }
            }else {
                exec { throw UnsupportedOperationException("Cannot run getter with autoLoad enabled") }
            }

            methodVisitor.visitJumpInsn(Opcodes.GOTO, controlFlow.continueJump)
        }
    }

    inline fun createIterableForEachLoop(iterableLocal: String, type: Class<*>, autoLoad: Boolean, exec: (getter: () -> Unit) -> Unit) {
        callIterable_iterator { loadLocal(iterableLocal) }
        storeLocal("$iterableLocal:Iterator", Iterator::class.java) {
            createIteratorForEachLoop("$iterableLocal:Iterator", type, autoLoad, exec)
        }
    }

    fun generateUniqueLocal(local: String): String {
        return "$local:${generatedLocals.size}"
    }

    fun generateUniqueLocal(): String {
        return "L:${generatedLocals.size}"
    }

    inline fun storeLocal(local: String, descriptor: Class<*>, exec: () -> Unit) {
        defineLocal(local, descriptor) {
            storeLocal(local)
            exec()
        }
    }

}