package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.*

class CodeWriter(val methodVisitor: MethodVisitor, val className: String) {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

    private val uuids: MutableSet<UUID> = mutableSetOf()

    fun defineLocal(local: String, descriptor: String, exec: (CodeWriter.() -> Unit)) {
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

        exec()
        methodVisitor.visitLabel(localEnd)

        locals.remove(local)
        availableLocals.add(localIndex)
    }

    fun defineGlobalLocal(local: String, descriptor: String) {
        val localStart = Label()
        methodVisitor.visitLabel(localStart)

        val localIndex: Int
        if(availableLocals.isEmpty()) {
            localIndex = locals.size
            val genLocal = Local(local, descriptor, localStart, null, localIndex)
            generatedLocals.add(genLocal)
            locals[local] = genLocal
        }else {
            localIndex = availableLocals.first()
            availableLocals.remove(localIndex)
        }
    }

    fun getLocal(local: String): Int? = locals[local]?.index

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
        generatedLocals.forEach {
            methodVisitor.visitLocalVariable(it.name, it.descriptor, null, it.start, it.end, it.index)
        }
    }

    fun loadLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor.startsWith('I')) {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index)
        }else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index)
        }
    }

    fun storeLocal(local: String) {
        val localVar = locals[local]!!
        if(localVar.descriptor.startsWith('I')) {
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

    fun writeUUID(value: UUID) {
        uuids.add(value)
        getStaticField(className, "uuid$${value.toString().replace("-", "")}", "Ljava/util/UUID;")
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

    fun executeIf(comparison: Int, exec: () -> Unit) {
        val endLabel = Label()
        when (comparison) {
            Opcodes.IFNONNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNULL, endLabel)
            Opcodes.IFNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, endLabel)
            else -> throw UnsupportedOperationException("Unknown comparison: $comparison")
        }
        exec()
        methodVisitor.visitLabel(endLabel)
    }

    fun executeIfElse(comparison: Int, exec: () -> Unit, execElse: () -> Unit) {
        val elseLabel = Label()
        val exitLabel = Label()
        when (comparison) {
            Opcodes.IFNONNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNULL, elseLabel)
            Opcodes.IFNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, elseLabel)
            else -> throw UnsupportedOperationException("Unknown comparison: $comparison")
        }
        exec()
        methodVisitor.visitJumpInsn(Opcodes.GOTO, exitLabel)
        methodVisitor.visitLabel(elseLabel)
        execElse()
        methodVisitor.visitLabel(exitLabel)
    }

    fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowRun) -> Unit) {
        val beginLabel = Label()
        val endLabel = Label()
        methodVisitor.visitLabel(beginLabel)
        condition(endLabel)
        exec(ControlFlowRun(
            { methodVisitor.visitJumpInsn(Opcodes.GOTO, beginLabel) },
            { methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel) }
        ))
        methodVisitor.visitLabel(endLabel)
    }

    fun createIndexedLoop(exec: (indexLocal: String, ControlFlowRun) -> Unit) {
        val baseName = "\$Indexer:${generatedLocals.size}"
        val sizeName = "$baseName:Size"
        val indexName = "$baseName:Index"
        defineLocal(sizeName, "I") {
            storeLocal(sizeName)

            defineLocal(indexName, "I") {
                writeInt(0)
                storeLocal(indexName)

                createWhileLoop({ brk ->
                    loadLocal(indexName)
                    loadLocal(sizeName)
                    methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, brk)
                }) { controlFlow ->
                    val contLabel = Label()
                    exec(indexName, ControlFlowRun(
                        { methodVisitor.visitJumpInsn(Opcodes.GOTO, contLabel) },
                        controlFlow.brk
                    ))
                    methodVisitor.visitLabel(contLabel)
                    methodVisitor.visitIincInsn(locals[indexName]!!.index, 1)
                    controlFlow.cont()
                }
            }
        }
    }

    fun createArrayListForEachLoop(arrayLocal: String, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        loadLocal(arrayLocal)
        callMethod("java/util/ArrayList", "size", "()I")
        createIndexedLoop { indexLocal, controlFlow ->
            exec(controlFlow) {
                loadLocal(arrayLocal)
                loadLocal(indexLocal)
                callMethod("java/util/ArrayList", "get", "(I)Ljava/lang/Object;")
            }
        }
    }

    fun createIteratorForEachLoop(iteratorLocal: String, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        createWhileLoop({ brk ->
            loadLocal(iteratorLocal)
            callInterfaceMethod("java/util/Iterator", "hasNext", "()Z")
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, brk)
        }) { controlFlow ->
            defineLocal("$iteratorLocal:Element", "Ljava/lang/Object;") {
                loadLocal(iteratorLocal)
                callInterfaceMethod("java/util/Iterator", "next", "()Ljava/lang/Object;")
                storeLocal("$iteratorLocal:Element")
                exec(controlFlow) { loadLocal("$iteratorLocal:Element") }
            }
            controlFlow.cont()
        }
    }

    fun createIterableForEachLoop(iterableLocal: String, exec: (ControlFlowRun, getter: () -> Unit) -> Unit) {
        defineLocal("$iterableLocal:Iterator", "Ljava/util/Iterator;") {
            loadLocal(iterableLocal)
            callInterfaceMethod("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;")
            storeLocal("$iterableLocal:Iterator")
            createIteratorForEachLoop("$iterableLocal:Iterator", exec)
        }
    }

    fun writeClinit(methodVisitor: MethodVisitor) {
        uuids.forEach {
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/UUID")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitLdcInsn(it.mostSignificantBits)
            methodVisitor.visitLdcInsn(it.leastSignificantBits)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/UUID", "<init>", "(JJ)V", false)
            methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "uuid$${it.toString().replace("-", "")}", "Ljava/util/UUID;")
        }
        methodVisitor.visitInsn(Opcodes.RETURN)
    }

    fun generateFields(classVisitor: ClassVisitor) {
        uuids.forEach {
            classVisitor.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "uuid$${it.toString().replace("-", "")}", "Ljava/util/UUID;", null, null)
        }
    }

}

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)
data class ControlFlowRun(val cont: () -> Unit, val brk: () -> Unit)