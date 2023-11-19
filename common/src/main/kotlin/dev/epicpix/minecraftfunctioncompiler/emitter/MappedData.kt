package dev.epicpix.minecraftfunctioncompiler.emitter

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Modifier

interface IMappedData

data class MappedFieldData(val clazz: String, val fieldName: String, val fieldType: String) : IMappedData {
    val primaryOpcode: Int
    init {
        val isStatic = Modifier.isStatic(Class.forName(clazz.replace('/', '.')).declaredFields.find { it.name == fieldName && it.type.descriptorString() == fieldType }!!.modifiers)
        primaryOpcode = if(isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD
    }
}

data class MappedFunctionData(val clazz: String, val methodName: String, val methodDescriptor: String) : IMappedData {
    val opcode: Int
    val isInterface: Boolean
    init {
        val method = Class.forName(clazz.replace('/', '.')).declaredMethods.find { it.name == methodName && Type.getMethodDescriptor(it) == methodDescriptor }!!
        opcode = if(Modifier.isStatic(method.modifiers)) Opcodes.INVOKESTATIC else if(method.declaringClass.isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        isInterface = method.declaringClass.isInterface
    }
}
