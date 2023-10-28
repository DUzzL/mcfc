package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.*

class CodeWriter(val methodVisitor: MethodVisitor, val className: String, val generateDebug: Boolean) {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

    private val uuids: MutableList<UUID> = mutableListOf()

    private var inControlFlow: Int = 0

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
        }else {
            localIndex = availableLocals.first()
            availableLocals.remove(localIndex)
        }
        val genLocal = Local(local, descriptor, localStart, null, localIndex)
        generatedLocals.add(genLocal)
        locals[local] = genLocal
    }

    fun storeLocal(local: String, descriptor: String, exec: (CodeWriter.() -> Unit)) {
        defineLocal(local, descriptor) {
            storeLocal(local)
            exec()
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
        if(generateDebug) {
            generatedLocals.forEach {
                methodVisitor.visitLocalVariable(it.name.replace(".", "_"), it.descriptor, null, it.start, it.end, it.index)
            }
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
        if(!uuids.contains(value)) {
            uuids.add(value)
        }
        if(generateDebug) {
            getStaticField(className, "uuid$${value.toString().replace("-", "")}", "Ljava/util/UUID;")
        }else {
            getStaticField(className, "u${uuids.indexOf(value)}", "Ljava/util/UUID;")
        }
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

    fun isInControlFlow(): Boolean {
        return inControlFlow != 0
    }

    fun executeIf(comparison: Int, exec: () -> Unit) {
        val endLabel = Label()
        when (comparison) {
            Opcodes.IFNONNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNULL, endLabel)
            Opcodes.IFNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, endLabel)
            Opcodes.IFNE -> methodVisitor.visitJumpInsn(Opcodes.IFNE, endLabel)
            Opcodes.IFEQ -> methodVisitor.visitJumpInsn(Opcodes.IFEQ, endLabel)
            else -> throw UnsupportedOperationException("Unknown comparison: $comparison")
        }
        inControlFlow++
        exec()
        inControlFlow--
        methodVisitor.visitLabel(endLabel)
    }

    fun executeIfElse(comparison: Int, exec: () -> Unit, execElse: () -> Unit) {
        val elseLabel = Label()
        val exitLabel = Label()
        when (comparison) {
            Opcodes.IFNONNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNULL, elseLabel)
            Opcodes.IFNULL -> methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, elseLabel)
            Opcodes.IFNE -> methodVisitor.visitJumpInsn(Opcodes.IFNE, elseLabel)
            Opcodes.IFEQ -> methodVisitor.visitJumpInsn(Opcodes.IFEQ, elseLabel)
            else -> throw UnsupportedOperationException("Unknown comparison: $comparison")
        }
        inControlFlow++
        exec()
        inControlFlow--
        methodVisitor.visitJumpInsn(Opcodes.GOTO, exitLabel)
        methodVisitor.visitLabel(elseLabel)
        inControlFlow++
        execElse()
        inControlFlow--
        methodVisitor.visitLabel(exitLabel)
    }

    fun createWhileLoop(condition: (`break`: Label) -> Unit, exec: (ControlFlowRun) -> Unit) {
        val beginLabel = Label()
        val endLabel = Label()
        methodVisitor.visitLabel(beginLabel)
        inControlFlow++
        condition(endLabel)
        exec(ControlFlowRun(
            { methodVisitor.visitJumpInsn(Opcodes.GOTO, beginLabel) },
            { methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel) }
        ))
        inControlFlow--
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
        loadLocal(iterableLocal)
        callInterfaceMethod("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;")
        storeLocal("$iterableLocal:Iterator", "Ljava/util/Iterator;") {
            createIteratorForEachLoop("$iterableLocal:Iterator", exec)
        }
    }

    fun writeClinit(methodVisitor: MethodVisitor) {
        uuids.forEachIndexed { index, uuid ->
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/UUID")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitLdcInsn(uuid.mostSignificantBits)
            methodVisitor.visitLdcInsn(uuid.leastSignificantBits)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/UUID", "<init>", "(JJ)V", false)
            if(generateDebug) {
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "uuid$${uuid.toString().replace("-", "")}", "Ljava/util/UUID;")
            }else {
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "u$index", "Ljava/util/UUID;")
            }
        }
        methodVisitor.visitInsn(Opcodes.RETURN)
    }

    fun generateFields(classVisitor: ClassVisitor) {
        uuids.forEachIndexed { index, uuid ->
            if(generateDebug) {
                classVisitor.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "uuid$${uuid.toString().replace("-", "")}", "Ljava/util/UUID;", null, null)
            }else {
                classVisitor.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "u$index", "Ljava/util/UUID;", null, null)
            }
        }
    }

    fun generateUniqueLocal(local: String): String {
        return "$local:${generatedLocals.size}"
    }

    fun updateControlFlow(count: Int) {
        inControlFlow += count
    }

}

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)
data class ControlFlowRun(val cont: () -> Unit, val brk: () -> Unit)