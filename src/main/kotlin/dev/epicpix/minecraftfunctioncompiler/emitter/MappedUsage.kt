package dev.epicpix.minecraftfunctioncompiler.emitter

import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.file.Files
import java.util.function.Consumer

private var isInitialized: Boolean = false

private val mappedUsageDefinitionType: String = MappedUsageDefinitions::class.java.descriptorString().run { substring(1, length - 1) }

fun initDefinitions() {
    if(isInitialized) return
    isInitialized = true
    val mod = FabricLoader.getInstance().getModContainer("minecraftfunctioncompiler").get()
    val thisPath = if(FabricLoader.getInstance().isDevelopmentEnvironment) {
        mod.rootPaths[0].resolve("../../classes/java/main/$mappedUsageDefinitionType.class").toAbsolutePath()
    } else {
        mod.findPath("$mappedUsageDefinitionType.class").get()
    }
    val bytes: ByteArray
    try {
        bytes = Files.readAllBytes(thisPath)
    } catch(e: IOException) {
        throw RuntimeException(e)
    }
    val reader = ClassReader(bytes)
    val visitor: ClassVisitor = MappedFunctionsClassVisitor(Opcodes.ASM9)
    reader.accept(visitor, ClassReader.SKIP_DEBUG)
}

private class MappedFunctionsClassVisitor(api: Int) : ClassVisitor(api) {
    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        val field = runCatching { MappedData::class.java.getDeclaredField(name) }
        if(field.isSuccess) {
            val f = field.getOrThrow()
            if(f.type == MappedData::class.java) {
                f.trySetAccessible()
                return MappedUsageMethodVisitor(api, f.get(null) as MappedData)
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}

private class MappedUsageMethodVisitor(api: Int, val data: MappedData) : MethodVisitor(api) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if(owner == mappedUsageDefinitionType) return
        data.generator = Consumer { it.methodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface) }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        if(owner == mappedUsageDefinitionType) return
        data.generator = Consumer { it.methodVisitor.visitFieldInsn(opcode, owner, name, descriptor) }
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }
}