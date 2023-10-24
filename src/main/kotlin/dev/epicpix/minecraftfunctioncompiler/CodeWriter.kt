package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class CodeWriter(val methodVisitor: MethodVisitor) {

    private val locals: MutableMap<String, Local> = mutableMapOf()
    private val availableLocals: MutableSet<Int> = mutableSetOf()
    private val generatedLocals: MutableList<Local> = mutableListOf()

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

    fun writeInstruction(instr: Int) {
        methodVisitor.visitInsn(instr)
    }

    fun castType(type: String) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, type)
    }

    fun createArrayListForEachLoop(arrayLocal: String, arrayIndexLocal: String, arraySizeLocal: String, exec: (getter: () -> Unit) -> Unit) {
        defineLocal(arraySizeLocal, "I") {
            loadLocal(arrayLocal)
            callMethod("java/util/ArrayList", "size", "()I")
            storeLocal(arraySizeLocal)

            defineLocal(arrayIndexLocal, "I") {
                writeInt(0)
                storeLocal(arrayIndexLocal)

                val beginLabel = Label()
                val endLabel = Label()
                methodVisitor.visitLabel(beginLabel)
                loadLocal(arrayIndexLocal)
                loadLocal(arraySizeLocal)
                methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, endLabel)
                exec {
                    loadLocal(arrayLocal)
                    loadLocal(arrayIndexLocal)
                    callMethod("java/util/ArrayList", "get", "(I)Ljava/lang/Object;")
                }
                methodVisitor.visitIincInsn(locals[arrayIndexLocal]!!.index, 1)
                methodVisitor.visitJumpInsn(Opcodes.GOTO, beginLabel)
                methodVisitor.visitLabel(endLabel)
            }
        }
    }

}

data class Local(val name: String, val descriptor: String, val start: Label, var end: Label?, val index: Int)