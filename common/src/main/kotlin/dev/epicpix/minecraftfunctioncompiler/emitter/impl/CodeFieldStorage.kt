package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.EMIT_SERVER_FUNCTION_MANAGER_NAME
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.*

class CodeFieldStorage(
    private val resourceLocationClass: Class<*>,
    private val commandFunctionClass: Class<*>,
    private val serverFunctionManagerClass: Class<*>,
    private val minecraftServerClass: Class<*>,
    private val mapFunction: (CodeFieldStorage, CodeWriter, DataLocation) -> Unit,
) {

    private val uuids: MutableList<UUID> = mutableListOf()
    private val resourceLocations: MutableList<DataLocation> = mutableListOf()
    private val functions: MutableList<DataLocation> = mutableListOf()

    private fun getUuidName(value: UUID) = if(DEBUG) "uuid\$${value.toString().replace("-", "")}" else "u${uuids.indexOf(value)}"
    private fun getResourceLocationName(value: DataLocation) = if(DEBUG) "resourceLocation\$${value.toString().replace('/', '$').replace(':', '$').replace('.', '$')}" else "r${resourceLocations.indexOf(value)}"
    private fun getFunctionName(value: DataLocation) = if(DEBUG) "function\$${value.toString().replace('/', '$').replace(':', '$').replace('.', '$')}" else "f${functions.indexOf(value)}"

    fun loadUUID(cw: CodeWriter, value: UUID) {
        if(!uuids.contains(value)) uuids.add(value)
        cw.getDataField(getUuidName(value), UUID::class.java)
    }

    fun loadResourceLocation(cw: CodeWriter, value: DataLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        cw.getDataField(getResourceLocationName(value), resourceLocationClass)
    }

    fun loadFunction(cw: CodeWriter, value: DataLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        if(!functions.contains(value)) functions.add(value)
        cw.getDataField(getFunctionName(value), commandFunctionClass)
    }

    fun loadServerFunctionManager(cw: CodeWriter) {
        cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass)
    }

    fun generate(classWriter: ClassWriter) {
        val mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "load", "(${minecraftServerClass.descriptorString()}${serverFunctionManagerClass.descriptorString()})V", null, null)
        mv.visitCode()
        val cw = CodeWriter(mv)

        mv.visitVarInsn(Opcodes.ALOAD, 1)
        cw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass)

        uuids.forEachIndexed { _, value ->
            cw.newObject(UUID::class.java, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!) {
                cw.writeLong(value.mostSignificantBits)
                cw.writeLong(value.leastSignificantBits)
            }
            cw.setDataField(getUuidName(value), UUID::class.java)
        }
        resourceLocations.forEachIndexed { _, value ->
            cw.newObject(resourceLocationClass, String::class.java, String::class.java) {
                cw.writeString(value.namespace)
                cw.writeString(value.path)
            }
            cw.setDataField(getResourceLocationName(value), resourceLocationClass)
        }
        functions.forEachIndexed { _, value ->
            mapFunction(this, cw, value)
            cw.setDataField(getFunctionName(value), commandFunctionClass)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null)
        uuids.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getUuidName(value), UUID::class.java.descriptorString(), null, null)
        }
        resourceLocations.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getResourceLocationName(value), resourceLocationClass.descriptorString(), null, null)
        }
        functions.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getFunctionName(value), commandFunctionClass.descriptorString(), null, null)
        }

    }

}
