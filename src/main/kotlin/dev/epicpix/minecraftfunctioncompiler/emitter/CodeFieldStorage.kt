package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.EMIT_SCOREBOARD_NAME
import dev.epicpix.minecraftfunctioncompiler.EMIT_SERVER_FUNCTION_MANAGER_NAME
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.*
import net.minecraft.commands.CommandFunction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import net.minecraft.server.ServerScoreboard
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.*

class CodeFieldStorage {

    private val uuids: MutableList<UUID> = mutableListOf()
    private val resourceLocations: MutableList<ResourceLocation> = mutableListOf()
    private val functions: MutableList<ResourceLocation> = mutableListOf()

    private fun getUuidName(value: UUID) = if(DEBUG) "uuid\$${value.toString().replace("-", "")}" else "u${uuids.indexOf(value)}"
    private fun getResourceLocationName(value: ResourceLocation) = if(DEBUG) "resourceLocation\$${value.toString().replace('/', '$').replace(':', '$')}" else "r${resourceLocations.indexOf(value)}"
    private fun getFunctionName(value: ResourceLocation) = if(DEBUG) "function\$${value.toString().replace('/', '$').replace(':', '$')}" else "f${functions.indexOf(value)}"

    fun loadUUID(cw: CodeWriter, value: UUID) {
        if(!uuids.contains(value)) uuids.add(value)
        cw.getDataField(getUuidName(value), UUID::class.java)
    }

    fun loadResourceLocation(cw: CodeWriter, value: ResourceLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        cw.getDataField(getResourceLocationName(value), ResourceLocation::class.java)
    }

    fun loadFunction(cw: CodeWriter, value: ResourceLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        if(!functions.contains(value)) functions.add(value)
        cw.getDataField(getFunctionName(value), CommandFunction::class.java)
    }

    fun loadServerFunctionManager(cw: CodeWriter) {
        cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, ServerFunctionManager::class.java)
    }

    fun loadScoreboard(cw: CodeWriter) {
        cw.getDataField(EMIT_SCOREBOARD_NAME, ServerScoreboard::class.java)
    }

    fun generate(classWriter: ClassWriter) {
        val mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "load", "(${MinecraftServer::class.java.descriptorString()}${ServerFunctionManager::class.java.descriptorString()})V", null, null)
        mv.visitCode()
        val cw = CodeWriter(mv)

        mv.visitVarInsn(Opcodes.ALOAD, 1)
        cw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, ServerFunctionManager::class.java)

        mv.visitVarInsn(Opcodes.ALOAD, 0)
        cw.writeMapped(MinecraftServer_getScoreboard)
        cw.setDataField(EMIT_SCOREBOARD_NAME, ServerScoreboard::class.java)

        uuids.forEachIndexed { _, value ->
            cw.newObject(UUID::class.java, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!) {
                cw.writeLong(value.mostSignificantBits)
                cw.writeLong(value.leastSignificantBits)
            }
            cw.setDataField(getUuidName(value), UUID::class.java)
        }
        resourceLocations.forEachIndexed { _, value ->
            cw.newObject(ResourceLocation::class.java, String::class.java, String::class.java) {
                mv.visitLdcInsn(value.namespace)
                mv.visitLdcInsn(value.path)
            }
            cw.setDataField(getResourceLocationName(value), ResourceLocation::class.java)
        }
        functions.forEachIndexed { _, value ->
            loadServerFunctionManager(cw)
            cw.writeMapped(ServerFunctionManager_getLibrary)
            cw.writeMapped(ServerFunctionLibrary_getFunctions)
            loadResourceLocation(cw, value)
            cw.writeMapped(Map_get)
            cw.castType(CommandFunction::class.java)
            cw.setDataField(getFunctionName(value), CommandFunction::class.java)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, ServerFunctionManager::class.java.descriptorString(), null, null)
        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SCOREBOARD_NAME, ServerScoreboard::class.java.descriptorString(), null, null)
        uuids.forEachIndexed { index, value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getUuidName(value), UUID::class.java.descriptorString(), null, null)
        }
        resourceLocations.forEachIndexed { index, value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getResourceLocationName(value), ResourceLocation::class.java.descriptorString(), null, null)
        }
        functions.forEachIndexed { index, value ->
            classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getFunctionName(value), CommandFunction::class.java.descriptorString(), null, null)
        }

    }

}
