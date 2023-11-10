package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.compiler.compile
import dev.epicpix.minecraftfunctioncompiler.data.updateRegistryMaps
import dev.epicpix.minecraftfunctioncompiler.emitter.CodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.emitter.CodeWriter
import dev.epicpix.minecraftfunctioncompiler.emitter.emitBytecode
import dev.epicpix.minecraftfunctioncompiler.emitter.initDefinitions
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.printInstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimize
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.parser.CommandParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandFunction.CommandEntry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files

class FunctionClassLoader(parent: ClassLoader?, private val functions: Map<ResourceLocation, CommandFunction>, private val minecraftServer: MinecraftServer) : ClassLoader(parent) {
    private val handles: MutableMap<ResourceLocation, MethodHandle> = HashMap()
    fun loadFunctions(serverFunctionManager: ServerFunctionManager) {
        initDefinitions()
        updateRegistryMaps()

        handles.clear()

        val definedFunctions = mutableMapOf<ResourceLocation, List<Command>>()

        val startParse = System.currentTimeMillis()
        funcLoop@
        for ((key, value) in functions) {
            try {
                for (entry in value.entries) {
                    if (entry !is CommandEntry) {
                        continue@funcLoop
                    }
                }
                definedFunctions[key] = CommandParser.parseFunctionCommands(value, minecraftServer)
            } catch (e: UnsupportedOperationException) {
                LOGGER.warn("Unable to parse function $key / ${e.message}")
            } catch (e: Throwable) {
                LOGGER.error("Unable to parse function $key", e)
            }
        }
        LOGGER.info("Parsed ${definedFunctions.size}/${functions.size} functions in ${System.currentTimeMillis()-startParse}ms")

        var totalPreoptimizedInstructions = 0
        var totalOptimizedInstructions = 0

        val compiledFunctions = mutableMapOf<ResourceLocation, List<Instruction>>()
        val startCompile = System.currentTimeMillis()
        for((key, value) in definedFunctions) {
            try {
                val compiled = compile(value)
                val optimized = optimize(compiled)
                compiledFunctions[key] = optimized
                if(DEBUG) {
                    compiled.forEach { totalPreoptimizedInstructions += it.countInstructions() }
                    optimized.forEach { totalOptimizedInstructions += it.countInstructions() }
                    LOGGER.info(" --- {} ---", key)
                    printInstructionList(optimized)
                }
            }catch(e: Throwable) {
                LOGGER.error("Failed to compile function ($key), clearing compilation data", e)
                definedFunctions.clear()
                LOGGER.info("Compiled 0 functions in ${System.currentTimeMillis()-startCompile}ms")
                return
            }
        }
        LOGGER.info("Compiled ${definedFunctions.size} functions in ${System.currentTimeMillis()-startCompile}ms")
        if(definedFunctions.size != 0 && DEBUG) {
            LOGGER.info("Average ${"%.2f".format(totalPreoptimizedInstructions/definedFunctions.size.toDouble())} instructions in pre-optimized IL bytecode")
            LOGGER.info("Average ${"%.2f".format(totalOptimizedInstructions/definedFunctions.size.toDouble())} instructions in optimized IL bytecode")
        }

        val storage = CodeFieldStorage()
        val loadedFunctions = mutableMapOf<ResourceLocation, Class<*>>()
        val startLoader = System.currentTimeMillis()
        for((key) in definedFunctions) {
            try {
                loadedFunctions[key] = defineFunction(key, storage, compiledFunctions[key]!!, definedFunctions)
            }catch(e: Throwable) {
                LOGGER.error("Failed to load function ($key), clearing data", e)
                definedFunctions.clear()
                LOGGER.info("Loaded 0 functions in ${System.currentTimeMillis()-startLoader}ms")
                return
            }
        }
        LOGGER.info("Loaded ${definedFunctions.size} functions in ${System.currentTimeMillis()-startLoader}ms")

        try {
            loadData(storage, serverFunctionManager)
        }catch(e: Throwable) {
            LOGGER.error("Failed to load data, clearing data", e)
            definedFunctions.clear()
            return
        }

        val startHandles = System.currentTimeMillis()
        try {
            for((key) in definedFunctions) {
                val handle = MethodHandles.lookup().findStatic(loadedFunctions[key], "run", MethodType.methodType(Int::class.javaPrimitiveType, CommandSourceStack::class.java))
                handles[key] = handle
            }
        }catch(e: Throwable) {
            LOGGER.error("Failed to create function handle, clearing data", e)
            definedFunctions.clear()
            handles.clear()
        }
        LOGGER.info("Loaded ${definedFunctions.size} function handles in ${System.currentTimeMillis()-startHandles}ms")
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

    private fun defineFunction(funcId: ResourceLocation, fieldStorage: CodeFieldStorage, instructions: List<Instruction>, definedFunctions: Map<ResourceLocation, List<Command>>): Class<*> {
        val className = "function/${funcId.namespace}/${funcId.path}"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run",
                "(${CommandSourceStack::class.java.descriptorString()})I", null, null)
        mv.visitCode()
        val codeWriter = CodeWriter(mv)
        val end = codeWriter.defineLocal("CommandSourceStack", CommandSourceStack::class.java)

        emitBytecode(instructions, fieldStorage, codeWriter, definedFunctions)

        codeWriter.end()
        end()

        mv.visitLdcInsn(definedFunctions[funcId]!!.size)
        mv.visitInsn(Opcodes.IRETURN)
        codeWriter.writeLocalVariables()
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        cw.visitEnd()
        return defineClass(className.replace('/', '.'), cw.toByteArray())
    }

    private fun loadData(fieldStorage: CodeFieldStorage, serverFunctionManager: ServerFunctionManager): Class<*> {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, DATA_CLASS, null, "java/lang/Object", null)
        fieldStorage.generate(cw)
        cw.visitEnd()

        val defined = defineClass(DATA_CLASS.replace('/', '.'), cw.toByteArray())
        defined.getDeclaredMethod("load", MinecraftServer::class.java, ServerFunctionManager::class.java).invoke(null, minecraftServer, serverFunctionManager)
        return defined
    }

    private fun defineClass(name: String, data: ByteArray): Class<*> {
        if(DEBUG) {
            runCatching { Files.createDirectory(FabricLoader.getInstance().gameDir.resolve("generated")) }
            Files.write(FabricLoader.getInstance().gameDir.resolve("generated").resolve("$name.class"), data)
        }
        return defineClass(name, data, 0, data.size)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}
