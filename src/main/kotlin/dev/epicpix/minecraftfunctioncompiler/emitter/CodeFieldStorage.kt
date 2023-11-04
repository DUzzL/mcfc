package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.DATA_CLASS
import dev.epicpix.minecraftfunctioncompiler.DEBUG
import dev.epicpix.minecraftfunctioncompiler.EMIT_SCOREBOARD_NAME
import dev.epicpix.minecraftfunctioncompiler.EMIT_SERVER_FUNCTION_MANAGER_NAME
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.*

class CodeFieldStorage {

    private val uuids: MutableList<UUID> = mutableListOf()
    private val resourceLocations: MutableList<ResourceLocation> = mutableListOf()
    private val functions: MutableList<ResourceLocation> = mutableListOf()

    private fun getUuidName(value: UUID) = if(DEBUG) "uuid\$${value.toString().replace("-", "")}" else "u${uuids.indexOf(value)}"
    private fun getResourceLocationName(value: ResourceLocation) = if(DEBUG) "resourceLocation\$${value.toString().replace('/', '$').replace(':', '$')}" else "r${resourceLocations.indexOf(value)}"
    private fun getFunctionName(value: ResourceLocation) = if(DEBUG) "function\$${value.toString().replace('/', '$').replace(':', '$')}" else "f${functions.indexOf(value)}"

    fun loadUUID(cw: CodeWriter, value: UUID) = loadUUID(cw.methodVisitor ,value)
    fun loadUUID(mv: MethodVisitor, value: UUID) {
        if(!uuids.contains(value)) uuids.add(value)
        mv.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, getUuidName(value), "Ljava/util/UUID;")
    }

    fun loadResourceLocation(cw: CodeWriter, value: ResourceLocation) = loadResourceLocation(cw.methodVisitor, value)
    fun loadResourceLocation(mv: MethodVisitor, value: ResourceLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        mv.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, getResourceLocationName(value), "Lnet/minecraft/resources/ResourceLocation;")
    }

    fun loadFunction(cw: CodeWriter, value: ResourceLocation) = loadFunction(cw.methodVisitor, value)
    fun loadFunction(mv: MethodVisitor, value: ResourceLocation) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value)
        if(!functions.contains(value)) functions.add(value)
        mv.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, getFunctionName(value), "Lnet/minecraft/commands/CommandFunction;")
    }

    fun loadServerFunctionManager(cw: CodeWriter) = loadServerFunctionManager(cw.methodVisitor)
    fun loadServerFunctionManager(mv: MethodVisitor) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, EMIT_SERVER_FUNCTION_MANAGER_NAME, "Lnet/minecraft/server/ServerFunctionManager;")
    }

    fun loadScoreboard(cw: CodeWriter) = loadScoreboard(cw.methodVisitor)
    fun loadScoreboard(mv: MethodVisitor) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, EMIT_SCOREBOARD_NAME, "Lnet/minecraft/server/ServerScoreboard;")
    }

    fun generate(cw: ClassWriter) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "load", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/ServerFunctionManager;)V", null, null)
        mv.visitCode()

        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, EMIT_SERVER_FUNCTION_MANAGER_NAME, "Lnet/minecraft/server/ServerFunctionManager;")

        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/MinecraftServer", "getScoreboard", "()Lnet/minecraft/server/ServerScoreboard;", false)
        mv.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, EMIT_SCOREBOARD_NAME, "Lnet/minecraft/server/ServerScoreboard;")

        uuids.forEachIndexed { _, value ->
            mv.visitTypeInsn(Opcodes.NEW, "java/util/UUID")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(value.mostSignificantBits)
            mv.visitLdcInsn(value.leastSignificantBits)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/UUID", "<init>", "(JJ)V", false)
            mv.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, getUuidName(value), "Ljava/util/UUID;")
        }
        resourceLocations.forEachIndexed { _, value ->
            mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/resources/ResourceLocation")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(value.namespace)
            mv.visitLdcInsn(value.path)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/resources/ResourceLocation", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false)
            mv.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, getResourceLocationName(value), "Lnet/minecraft/resources/ResourceLocation;")
        }
        functions.forEachIndexed { _, value ->
            loadServerFunctionManager(mv)
            mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/server/ServerFunctionManager", "library", "Lnet/minecraft/server/ServerFunctionLibrary;")
            mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/server/ServerFunctionLibrary", "functions", "Ljava/util/Map;")
            loadResourceLocation(mv, value)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true)
            mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/commands/CommandFunction")
            mv.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, getFunctionName(value), "Lnet/minecraft/commands/CommandFunction;")
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, "Lnet/minecraft/server/ServerFunctionManager;", null, null)
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, EMIT_SCOREBOARD_NAME, "Lnet/minecraft/server/ServerScoreboard;", null, null)
        uuids.forEachIndexed { index, value ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getUuidName(value), "Ljava/util/UUID;", null, null)
        }
        resourceLocations.forEachIndexed { index, value ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getResourceLocationName(value), "Lnet/minecraft/resources/ResourceLocation;", null, null)
        }
        functions.forEachIndexed { index, value ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, getFunctionName(value), "Lnet/minecraft/commands/CommandFunction;", null, null)
        }

    }

}
