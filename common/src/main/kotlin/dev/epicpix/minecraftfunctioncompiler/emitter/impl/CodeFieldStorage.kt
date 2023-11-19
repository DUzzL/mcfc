package dev.epicpix.minecraftfunctioncompiler.emitter.impl

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.EMIT_SCOREBOARD_NAME
import dev.epicpix.minecraftfunctioncompiler.EMIT_SERVER_FUNCTION_MANAGER_NAME
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.ICodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.emitter.ICodeWriter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.*

class CodeFieldStorage(
    private val resourceLocationClass: Class<*>,
    private val commandFunctionClass: Class<*>,
    private val attributeClass: Class<*>,
    private val serverFunctionManagerClass: Class<*>,
    private val minecraftServerClass: Class<*>,
    private val serverScoreboardClass: Class<*>,
    private val mapScoreboard: ICodeFieldStorage.(CodeWriter) -> Unit,
    private val mapFunction: ICodeFieldStorage.(CodeWriter, DataLocation) -> Unit,
    private val mapAttribute: ICodeFieldStorage.(CodeWriter, DataLocation) -> Unit,
) : ICodeFieldStorage {

    private val uuids: MutableList<UUID> = mutableListOf()
    private val resourceLocations: MutableList<DataLocation> = mutableListOf()
    private val functions: MutableList<DataLocation> = mutableListOf()
    private val attributes: MutableList<DataLocation> = mutableListOf()

    private fun getUuidName(value: UUID) = if(DEBUG) "uuid\$${value.toString().replace("-", "")}" else "u${uuids.indexOf(value)}"
    private fun getResourceLocationName(value: DataLocation) = if(DEBUG) "resourceLocation\$${value.toString().replace('/', '$').replace(':', '$').replace('.', '$')}" else "r${resourceLocations.indexOf(value)}"
    private fun getFunctionName(value: DataLocation) = if(DEBUG) "function\$${value.toString().replace('/', '$').replace(':', '$').replace('.', '$')}" else "f${functions.indexOf(value)}"
    private fun getAttributeName(value: DataLocation) = if(DEBUG) "attribute\$${value.toString().replace('/', '$').replace(':', '$').replace('.', '$')}" else "f${attributes.indexOf(value)}"

    override fun loadUUID(cw: ICodeWriter, value: UUID) {
        if(!uuids.contains(value)) uuids.add(value)
        cw.getDataField(getUuidName(value), UUID::class.java)
    }

    override fun loadResourceLocation(cw: ICodeWriter, value: DataLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        cw.getDataField(getResourceLocationName(value), resourceLocationClass)
    }

    override fun loadFunction(cw: ICodeWriter, value: DataLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        if(!functions.contains(value)) functions.add(value)
        cw.getDataField(getFunctionName(value), commandFunctionClass)
    }

    override fun loadAttribute(cw: ICodeWriter, value: DataLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        if(!attributes.contains(value)) attributes.add(value)
        cw.getDataField(getAttributeName(value), attributeClass)
    }

    override fun loadServerFunctionManager(cw: ICodeWriter) {
        cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass)
    }

    override fun loadScoreboard(cw: ICodeWriter) {
        cw.getDataField(EMIT_SCOREBOARD_NAME, serverScoreboardClass)
    }

    override fun generate(classWriter: ClassWriter) {
        val mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "load", "(${minecraftServerClass.descriptorString()}${serverFunctionManagerClass.descriptorString()})V", null, null)
        mv.visitCode()
        val cw = CodeWriter(mv)

        mv.visitVarInsn(Opcodes.ALOAD, 1)
        cw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass)

        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mapScoreboard(cw)
        cw.setDataField(EMIT_SCOREBOARD_NAME, serverScoreboardClass)

        uuids.forEachIndexed { _, value ->
            val createEnd = cw.newObject(UUID::class.java, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!)
            cw.writeLong(value.mostSignificantBits)
            cw.writeLong(value.leastSignificantBits)
            createEnd()
            cw.setDataField(getUuidName(value), UUID::class.java)
        }
        resourceLocations.forEachIndexed { _, value ->
            val createEnd = cw.newObject(resourceLocationClass, String::class.java, String::class.java)
            mv.visitLdcInsn(value.namespace)
            mv.visitLdcInsn(value.path)
            createEnd()
            cw.setDataField(getResourceLocationName(value), resourceLocationClass)
        }
        functions.forEachIndexed { _, value ->
            mapFunction(cw, value)
            cw.setDataField(getFunctionName(value), commandFunctionClass)
        }
        attributes.forEachIndexed { _, value ->
            mapAttribute(cw, value)
            cw.setDataField(getAttributeName(value), attributeClass)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null)
        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SCOREBOARD_NAME, serverScoreboardClass.descriptorString(), null, null)
        uuids.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getUuidName(value), UUID::class.java.descriptorString(), null, null)
        }
        resourceLocations.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getResourceLocationName(value), resourceLocationClass.descriptorString(), null, null)
        }
        functions.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getFunctionName(value), commandFunctionClass.descriptorString(), null, null)
        }
        attributes.forEach { value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getAttributeName(value), attributeClass.descriptorString(), null, null)
        }

    }

}
