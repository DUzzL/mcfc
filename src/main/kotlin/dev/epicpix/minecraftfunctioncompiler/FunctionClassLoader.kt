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
import java.lang.UnsupportedOperationException
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
            } catch (e: UnsupportedOperationException) {
                LOGGER.warn("Unable to compile function " + key + " / " + e.message)
            } catch (e: Exception) {
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
            LOGGER.error("Failed to run function $location", e)
            throw RuntimeException(e)
        }
    }

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
        val codeWriter = CodeWriter(mv, name.replace('.', '/'))

        codeWriter.defineLocal("CommandSourceStack", "Lnet/minecraft/commands/CommandSourceStack;") {
            for (entry in function.entries) {
                if (entry is CommandEntry) {
                    val nodes = mutableListOf<BrigadierNode>()

                    var context: CommandContextBuilder<CommandSourceStack> = entry.parse.context
                    while(true) {
                        for(node in context.nodes) {
                            nodes.add(BrigadierNode(entry.parse.reader, node.range, node.node.name, context.arguments[node.node.name] ?: node.node.name))
                        }
                        if(context.child == null) {
                            break
                        }
                        context = context.child
                    }
                    println(nodes)

                    val commandName = context.nodes[0].node.name
                    generateCommand(commandName, entry.parse.reader, context, codeWriter)
                }
            }
        }
        codeWriter.end()

        mv.visitLdcInsn(function.entries.size)
        mv.visitInsn(Opcodes.IRETURN)
        codeWriter.writeLocalVariables()
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        codeWriter.generateFields(cw)

        val cmv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "<clinit>",
            "()V", null, null)
        cmv.visitCode()
        codeWriter.writeClinit(cmv)
        cmv.visitMaxs(0, 0)
        cmv.visitEnd()


        cw.visitEnd()
        val data = cw.toByteArray()
        Files.write(FabricLoader.getInstance().gameDir.resolve("generated").resolve("$name.class"), data)
        return defineClass(name, data, 0, data.size)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}
