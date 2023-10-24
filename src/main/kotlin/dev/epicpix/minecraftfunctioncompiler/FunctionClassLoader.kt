package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.context.CommandContextBuilder
import dev.epicpix.minecraftfunctioncompiler.CodeGenerator.generateCommand
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandFunction.CommandEntry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files

class FunctionClassLoader(parent: ClassLoader?, private val functions: Map<ResourceLocation, CommandFunction>) : ClassLoader(parent) {
    private val handles: MutableMap<ResourceLocation, MethodHandle> = HashMap()
    fun loadFunctions() {
        handles.clear()
        for ((key) in functions) {
            try {
                val clz = loadClass("function." + key.namespace + "." + key.path.replace('/', '.'))
                val handle = MethodHandles.lookup().findStatic(clz, "run", MethodType.methodType(Int::class.javaPrimitiveType, CommandSourceStack::class.java))
                handles[key] = handle
            } catch (e: NoSuchMethodException) {
                LOGGER.warn("Unable to compile function " + key + " / " + e.message)
            } catch (e: IllegalAccessException) {
                LOGGER.warn("Unable to compile function $key", e)
            } catch (e: ClassNotFoundException) {
                LOGGER.warn("Unable to compile function $key", e)
            } catch (e: UnsupportedOperationException) {
                LOGGER.warn("Unable to compile function $key", e)
            }
        }
    }

    fun hasFunctionLoaded(location: ResourceLocation): Boolean {
        return handles.containsKey(location)
    }

    fun runFunction(location: ResourceLocation, stack: CommandSourceStack?): Int {
        return try {
            handles[location]!!.invoke(stack) as Int
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        if (!name.startsWith("function.")) {
            return super.findClass(name)
        }
        val n = name.substring("function.".length).replace('.', '/')
        val parts = n.split("/".toRegex(), limit = 2).toTypedArray()
        val location = ResourceLocation(parts[0], parts[1])
        var function: CommandFunction? = null
        for ((key, value) in functions) {
            if (key == location) {
                function = value
            }
        }
        if (function == null) {
            return super.findClass(name)
        }
        for (entry in function.entries) {
            if (entry !is CommandEntry) {
                return super.findClass(name)
            }
        }
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run",
                "(Lnet/minecraft/commands/CommandSourceStack;)I", null, null)
        mv.visitCode()
        val locals = HashMap<String, Int?>()
        locals["CommandSourceStack"] = 0
        for (entry in function.entries) {
            if (entry is CommandEntry) {
                val context: CommandContextBuilder<CommandSourceStack> = entry.parse.context
                val commandName = context.nodes[0].node.name
                generateCommand(commandName, entry.parse.reader, context, locals, mv)
            }
        }
        mv.visitLdcInsn(function.entries.size)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        val data = cw.toByteArray()
        try {
            Files.write(FabricLoader.getInstance().gameDir.resolve("generated").resolve("$name.class"), data)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return defineClass(name, data, 0, data.size)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}
