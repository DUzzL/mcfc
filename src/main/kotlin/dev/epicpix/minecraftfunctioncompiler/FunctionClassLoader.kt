package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.CodeGenerator.generateCommandLike
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandFunction.CommandEntry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import kotlin.UnsupportedOperationException

class FunctionClassLoader(parent: ClassLoader?, private val functions: Map<ResourceLocation, CommandFunction>) : ClassLoader(parent) {
    private val handles: MutableMap<ResourceLocation, MethodHandle> = HashMap()
    fun loadFunctions() {
        handles.clear()

        val definedFunctions = mutableMapOf<ResourceLocation, List<CommandLike>>()

        val startParse = System.currentTimeMillis()
        funcLoop@
        for ((key, value) in functions) {
            try {
                for (entry in value.entries) {
                    if (entry !is CommandEntry) {
                        continue@funcLoop
                    }
                }
                definedFunctions[key] = CommandParser.parseFunctionCommands(value)
            } catch (e: UnsupportedOperationException) {
                LOGGER.warn("Unable to parse function " + key + " / " + e.message)
            } catch (e: Throwable) {
                LOGGER.error("Unable to parse function $key", e)
            }
        }
        LOGGER.info("Parsed ${definedFunctions.size} functions in ${System.currentTimeMillis()-startParse}ms")
        println(definedFunctions)

        val startLoader = System.currentTimeMillis()
        try {
            for((key) in definedFunctions) {
                val clz = defineFunction(key, definedFunctions)
                val handle = MethodHandles.lookup().findStatic(clz, "run", MethodType.methodType(Int::class.javaPrimitiveType, CommandSourceStack::class.java))
                handles[key] = handle
            }
        }catch(e: Throwable) {
            LOGGER.error("Failed to compile function, clearing compilation data", e)
            definedFunctions.clear()
            handles.clear()
        }
        LOGGER.info("Loaded ${definedFunctions.size} functions in ${System.currentTimeMillis()-startLoader}ms")
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

    private fun defineFunction(funcId: ResourceLocation, definedFunctions: Map<ResourceLocation, List<CommandLike>>): Class<*> {
        val className = "function/${funcId.namespace}/${funcId.path}"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run",
                "(Lnet/minecraft/commands/CommandSourceStack;)I", null, null)
        mv.visitCode()
        val codeWriter = CodeWriter(mv, className, generateDebug = true)

        val commands = definedFunctions[funcId]!!

        val context = GeneratorCommandContext()
        codeWriter.defineLocal("CommandSourceStack", "Lnet/minecraft/commands/CommandSourceStack;") {
            commands.forEach {
                generateCommandLike(it, context, codeWriter)
            }
        }
        codeWriter.end()

        mv.visitLdcInsn(commands.size)
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
        Files.write(FabricLoader.getInstance().gameDir.resolve("generated").resolve("${className.replace('/', '.')}.class"), data)
        return defineClass(className.replace('/', '.'), data, 0, data.size)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}
