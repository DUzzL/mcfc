package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class CodeWriter(val methodVisitor: MethodVisitor) {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

    fun getLocalIndex(local: String) = locals[local]!!.index

    fun defineLocal(local: String, descriptor: String): () -> Unit {
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
        locals[local] = Local(local, descriptor, localStart, localEnd, localIndex)
        generatedLocals.add(locals[local]!!)

        return {
            methodVisitor.visitLabel(localEnd)

            locals.remove(local)
            availableLocals.add(localIndex)
        }
    }

    fun defineGlobalLocal(local: String, descriptor: String) {
        val localStart = Label()
        methodVisitor.visitLabel(localStart)

        val localIndex: Int
        if(availableLocals.isEmpty()) {
            localIndex = locals.size
        }else {
            localIndex = availableLocals.first()
            availableLocals.remove(localIndex)
        }
        val genLocal = Local(local, descriptor, localStart, null, localIndex)
        generatedLocals.add(genLocal)
        locals[local] = genLocal
    }

    fun storeLocal(local: String, descriptor: String, exec: (CodeWriter.() -> Unit)) {
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
        if(localVar.descriptor == "I") {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index)
        }
    }

    fun storeLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor == "I") {
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

    fun writeString(str: String) {
        methodVisitor.visitLdcInsn(str)
    }

    fun writeNull() {
        methodVisitor.visitInsn(Opcodes.ACONST_NULL)
    }

    fun newObject(owner: String, descriptor: String, argumentProvider: CodeWriter.() -> Unit) {
        methodVisitor.visitTypeInsn(Opcodes.NEW, owner)
        writeInstruction(Opcodes.DUP)
        argumentProvider()
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", descriptor, false)
    }

    fun callFunction(owner: String, name: String, descriptor: String) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, descriptor, false)
    }

    fun callMethod(owner: String, name: String, descriptor: String) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false)
    }

    fun callInterfaceMethod(owner: String, name: String, descriptor: String) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true)
    }

    fun getObjectField(owner: String, name: String, descriptor: String) {
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, owner, name, descriptor)
    }

    fun getStaticField(owner: String, name: String, descriptor: String) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, owner, name, descriptor)
    }

    fun setStaticField(owner: String, name: String, descriptor: String) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, descriptor)
    }

    fun writeInstruction(instr: Int) {
        methodVisitor.visitInsn(instr)
    }

    fun castType(type: String) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, type)
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
        storeLocal(sizeName, "I") {

            writeInt(0)
            storeLocal(indexName, "I") {

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

    fun createListForEachLoop(arrayLocal: String, type: String, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        loadLocal(arrayLocal)
        callInterfaceMethod("java/util/List", "size", "()I")
        createIndexedLoop { indexLocal, controlFlow ->
            if(autoLoad) {
                loadLocal(arrayLocal)
                loadLocal(indexLocal)
                callInterfaceMethod("java/util/List", "get", "(I)Ljava/lang/Object;")
                castType(type)
                exec(controlFlow) { throw UnsupportedOperationException("Cannot run getter with autoLoad enabled") }
            }else {
                exec(controlFlow) {
                    loadLocal(arrayLocal)
                    loadLocal(indexLocal)
                    callInterfaceMethod("java/util/List", "get", "(I)Ljava/lang/Object;")
                    castType(type)
                }
            }
        }
    }

    fun createIteratorForEachLoop(iteratorLocal: String, type: String, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        createWhileLoop({ brk ->
            loadLocal(iteratorLocal)
            callInterfaceMethod("java/util/Iterator", "hasNext", "()Z")
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, brk)
        }) { controlFlow ->
            loadLocal(iteratorLocal)
            callInterfaceMethod("java/util/Iterator", "next", "()Ljava/lang/Object;")
            if(type != "java/lang/Object") castType(type)

            if(!autoLoad) {
                val end = defineLocal("$iteratorLocal:Element", "L$type;")
                storeLocal("$iteratorLocal:Element")

                exec(controlFlow) { loadLocal("$iteratorLocal:Element") }
                end()
            }else {
                exec(controlFlow) { throw UnsupportedOperationException("autoLoad enabled, cannot use getter") }
            }

            controlFlow.cont()
        }
    }

    fun createIterableForEachLoop(iterableLocal: String, type: String, autoLoad: Boolean = false, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        loadLocal(iterableLocal)
        callInterfaceMethod("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;")
        storeLocal("$iterableLocal:Iterator", "Ljava/util/Iterator;") {
            createIteratorForEachLoop("$iterableLocal:Iterator", type, autoLoad, exec)
        }
    }

    fun generateUniqueLocal(local: String): String {
        return "$local:${generatedLocals.size}"
    }

}

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)
data class ControlFlowRun(val codeWriter: CodeWriter, val continueJump: Label, val breakJump: Label) {
    fun cont() = codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, continueJump)
    fun cont(condition: Int) = codeWriter.methodVisitor.visitJumpInsn(condition, continueJump)

    fun brk() = codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, breakJump)
    fun brk(condition: Int) = codeWriter.methodVisitor.visitJumpInsn(condition, breakJump)
}