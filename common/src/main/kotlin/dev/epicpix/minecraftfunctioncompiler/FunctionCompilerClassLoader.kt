package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext
import dev.epicpix.minecraftfunctioncompiler.emitter.ICodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.emitter.generateGraphs
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType
import dev.epicpix.minecraftfunctioncompiler.il.printInstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimize
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

abstract class FunctionCompilerClassLoader(
    parent: ClassLoader?,
    private val generatedDir: Path,
    private val commandSourceStackClass: Class<*>,
    private val minecraftServerClass: Class<*>,
    private val serverFunctionManagerClass: Class<*>,
    private val minecraftServer: Any,
    val logInfo: (String) -> Unit,
    val logWarn: (String) -> Unit,
    val logError: (String) -> Unit,
) : ClassLoader(parent) {

    abstract fun registerFunction(clazz: Class<*>, key: DataLocation)
    abstract fun resetHandles()
    abstract fun handleCount(): Int

    fun loadFunctionClasses(
        definedFunctions: Map<DataLocation, List<Command>>,
        fieldStorage: ICodeFieldStorage,
        serverFunctionManager: Any,
        compile: (List<Command>) -> List<Instruction>,
        emitBytecode: (List<Instruction>, EmitterContext) -> Unit,
    ) {
        var totalPreoptimizedInstructions = 0
        var totalOptimizedInstructions = 0

        val compiledFunctions = mutableMapOf<DataLocation, List<Instruction>>()
        val startCompile = System.currentTimeMillis()
        for((key, value) in definedFunctions) {
            try {
                val compiled = compile(value)
                val optimized = optimize(compiled)
                compiledFunctions[key] = optimized
                if(DEBUG) {
                    compiled.forEach { totalPreoptimizedInstructions += it.countInstructions() }
                    optimized.forEach { totalOptimizedInstructions += it.countInstructions() }
                    logInfo(" --- $key ---")
                    printInstructionList(optimized, logInfo)
                }
            }catch(e: UnsupportedOperationException) {
                logWarn("Failed to compile function $key / ${e.message}")
            }catch(e: Throwable) {
                logError("Failed to compile function $key")
                e.printStackTrace()
            }
        }
        logInfo("Compiled ${compiledFunctions.size} functions in ${System.currentTimeMillis()-startCompile}ms")
        if(DEBUG && compiledFunctions.isNotEmpty()) {
            logInfo("Average ${"%.2f".format(totalPreoptimizedInstructions/compiledFunctions.size.toDouble())} instructions in pre-optimized IL bytecode")
            logInfo("Average ${"%.2f".format(totalOptimizedInstructions/compiledFunctions.size.toDouble())} instructions in optimized IL bytecode")
        }

        val compiledFunctionSet = compiledFunctions.keys.toSet()
        val loadedFunctions = mutableMapOf<DataLocation, Class<*>>()
        val startLoader = System.currentTimeMillis()
        for((key) in compiledFunctions) {
            try {
                loadedFunctions[key] = defineFunction(key, fieldStorage, compiledFunctions[key]!!, definedFunctions[key]!!.size, compiledFunctionSet, emitBytecode)
            }catch(e: Throwable) {
                logError("Failed to load function ($key), clearing data")
                e.printStackTrace()
                logInfo("Loaded 0 functions in ${System.currentTimeMillis()-startLoader}ms")
                return
            }
        }
        logInfo("Loaded ${compiledFunctions.size} functions in ${System.currentTimeMillis()-startLoader}ms")
        if(DEBUG) {
            runCatching { Files.createDirectory(generatedDir) }
            val startGraph = System.currentTimeMillis()
            generateGraphs(compiledFunctions, generatedDir)
            logInfo("Generated graphs in ${System.currentTimeMillis()-startGraph}ms")
        }

        try {
            loadData(fieldStorage, serverFunctionManager)
        }catch(e: Throwable) {
            logError("Failed to load data, clearing data")
            e.printStackTrace()
            return
        }

        val startHandles = System.currentTimeMillis()
        for((key) in compiledFunctions) {
            try {
                registerFunction(loadedFunctions[key]!!, key)
            }catch(e: Throwable) {
                logError("Failed to create function handle ($key), clearing data")
                e.printStackTrace()
                resetHandles()
                logInfo("Instruction dump:")
                printInstructionList(compiledFunctions[key]!!, logInfo)
                break
            }
        }
        logInfo("Loaded ${handleCount()} function handles in ${System.currentTimeMillis()-startHandles}ms")
    }

    private fun defineFunction(funcId: DataLocation, fieldStorage: ICodeFieldStorage, instructions: List<Instruction>, commandCount: Int, compiledFunctions: Set<DataLocation>, emitBytecode: (List<Instruction>, EmitterContext) -> Unit): Class<*> {
        val className = "function/${funcId.namespace}/${funcId.path}"

        val cw = createClassWriter()
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run",
            "(${commandSourceStackClass.descriptorString()})I", null, null)
        mv.visitCode()
        val codeWriter = CodeWriter(mv)
        val end = codeWriter.defineLocal("CommandSourceStack", commandSourceStackClass)

        emitBytecode(instructions, EmitterContext(fieldStorage, codeWriter, compiledFunctions, mapOf(), mapOf(), Instruction(InstructionType("<NONE>"), mapOf()), mutableMapOf(), mutableMapOf()))

        codeWriter.endLabels()
        end()

        mv.visitLdcInsn(commandCount)
        mv.visitInsn(Opcodes.IRETURN)
        codeWriter.writeLocalVariables()
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        cw.visitEnd()
        return defineClass(className.replace('/', '.'), cw.toByteArray())
    }

    private fun loadData(fieldStorage: ICodeFieldStorage, serverFunctionManager: Any): Class<*> {
        val cw = createClassWriter()
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, DATA_CLASS, null, "java/lang/Object", null)
        fieldStorage.generate(cw)
        cw.visitEnd()

        val defined = defineClass(DATA_CLASS.replace('/', '.'), cw.toByteArray())
        defined.getDeclaredMethod("load", minecraftServerClass, serverFunctionManagerClass).invoke(null, minecraftServer, serverFunctionManager)
        return defined
    }

    private fun createClassWriter(): ClassWriter {
        return object : ClassWriter(COMPUTE_FRAMES) {
            override fun getCommonSuperClass(type1: String, type2: String): String {
                var c: Class<*>
                val d: Class<*>
                try {
                    c = Class.forName(type1.replace('/', '.'), false, classLoader)
                    d = Class.forName(type2.replace('/', '.'), false, classLoader)
                } catch(e: Exception) {
                    throw RuntimeException(e)
                }
                return when {
                    c.isAssignableFrom(d) -> type1
                    d.isAssignableFrom(c) -> type2
                    c.isInterface || d.isInterface -> "java/lang/Object"
                    else -> {
                        do {
                            c = c.superclass
                        } while (!c.isAssignableFrom(d))
                        c.name.replace('.', '/')
                    }
                }
            }
        }
    }

    private fun defineClass(name: String, data: ByteArray): Class<*> {
        if(DEBUG) {
            runCatching { Files.createDirectory(generatedDir) }
            Files.write(generatedDir.resolve("$name.class"), data)
        }
        return defineClass(name, data, 0, data.size)
    }

}