package dev.epicpix.minecraftfunctioncompiler.emitter

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.ServerFunctionManager
import net.minecraft.server.ServerFunctionManager.TraceCallbacks
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import org.objectweb.asm.Opcodes
import java.util.*

fun CodeWriter.callMethodGetEntity() {
    val resolver = FabricLoader.getInstance().mappingResolver
    val owner = ServerLevel::class.java.getRawDescriptor()
    val descriptor = arrayOf(UUID::class.java).getMethodDescriptor(Entity::class.java)
    val methodName = when(resolver.currentRuntimeNamespace) {
        "intermediary" -> resolver.mapMethodName("intermediary", owner.replace('/', ','), "method_14190", descriptor)
        "named" -> "getEntity"
        else -> throw UnsupportedOperationException("Unsupported runtime namespace: ${resolver.currentRuntimeNamespace}")
    }
    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, methodName, descriptor, false)
}

fun CodeWriter.callMethodExecuteCommand() {
    val resolver = FabricLoader.getInstance().mappingResolver
    val owner = ServerFunctionManager::class.java.getRawDescriptor()
    val descriptor = arrayOf(CommandFunction::class.java, CommandSourceStack::class.java, TraceCallbacks::class.java, CompoundTag::class.java).getMethodDescriptor(Int::class.javaPrimitiveType!!)
    val methodName = when(resolver.currentRuntimeNamespace) {
        "intermediary" -> resolver.mapMethodName("intermediary", owner.replace('/', ','), "method_36341", descriptor)
        "named" -> "execute"
        else -> throw UnsupportedOperationException("Unsupported runtime namespace: ${resolver.currentRuntimeNamespace}")
    }
    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, methodName, descriptor, false)
}
