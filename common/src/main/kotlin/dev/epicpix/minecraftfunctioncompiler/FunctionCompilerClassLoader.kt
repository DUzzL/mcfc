package dev.epicpix.minecraftfunctioncompiler

import com.google.gson.JsonObject
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext
import dev.epicpix.minecraftfunctioncompiler.emitter.ICodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.emitter.generateGraphs
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2
import dev.epicpix.minecraftfunctioncompiler.il.printInstructionList
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimize
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter
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

    fun loadFunctionClasses(
        foundFunctionCount: Int,
        parseTime: Double,
        definedFunctions: Map<DataLocation, List<Command>>,
        fieldStorage: ICodeFieldStorage,
        serverFunctionManager: Any,
        compile: (List<Command>) -> List<Instruction>,
    ) {
        var totalPreoptimizedInstructions = 0
        var totalOptimizedInstructions = 0

        val compiledFunctions = mutableMapOf<DataLocation, List<Instruction>>()
        val startCompile = System.nanoTime()
        for((key, value) in definedFunctions) {
            try {
                val compiled = compile(value)
                compiled.forEach { totalPreoptimizedInstructions += it.countInstructions() }
                val optimized = optimize(compiled)
                optimized.forEach { totalOptimizedInstructions += it.countInstructions() }
                compiledFunctions[key] = listOf(Instructions2.addStaticRequirements(optimized))
                if(DEBUG) {
                    logInfo(" --- $key ---")
                    printInstructionList(compiledFunctions[key]!!, logInfo)
                }
            }catch(e: UnsupportedOperationException) {
                logWarn("Failed to compile function $key / ${e.message}")
            }catch(e: Throwable) {
                logError("Failed to compile function $key")
                e.printStackTrace()
                FunctionCompilerReporter.reportException("compile_function", e, JsonObject())
            }
        }
        val compileTime = (System.nanoTime()-startCompile)/1000000000.0
        val compiledFunctionSet = compiledFunctions.keys.toSet()
        val loadedFunctions = mutableMapOf<DataLocation, Class<*>>()
        val startLoader = System.nanoTime()
        for((key) in compiledFunctions) {
            try {
                loadedFunctions[key] = defineFunction(key, fieldStorage, compiledFunctions[key]!!, definedFunctions[key]!!.size, compiledFunctionSet)
            }catch(e: Throwable) {
                logError("Failed to load function ($key), skipping function compilation")
                e.printStackTrace()
                FunctionCompilerReporter.reportExceptionWithInstructions("define_function", e, compiledFunctions[key]!!)
                FunctionCompilerReporter.sendReports()
                return
            }
        }

        try {
            loadData(fieldStorage, serverFunctionManager)
        }catch(e: Throwable) {
            logError("Failed to load data, clearing data")
            e.printStackTrace()
            FunctionCompilerReporter.reportException("define_data_class", e, JsonObject())
            FunctionCompilerReporter.sendReports()
            return
        }
        val classLoadTime = (System.nanoTime()-startLoader)/1000000000.0
        if(DEBUG) {
            runCatching { Files.createDirectory(generatedDir) }
            val startGraph = System.nanoTime()
            generateGraphs(compiledFunctions, generatedDir)
            logInfo("Generated graphs in ${(System.nanoTime()-startGraph)/1000000}ms")
        }

        val startHandles = System.nanoTime()
        for((key) in compiledFunctions) {
            try {
                registerFunction(loadedFunctions[key]!!, key)
            }catch(e: Throwable) {
                logError("Failed to create function handle ($key), clearing data")
                e.printStackTrace()
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, compiledFunctions[key]!!)
                FunctionCompilerReporter.sendReports()
                resetHandles()
                logInfo("Instruction dump:")
                printInstructionList(compiledFunctions[key]!!, logInfo)
                break
            }
        }
        val handleLoadTime = (System.nanoTime()-startHandles)/1000000000.0
        FunctionCompilerReporter.reportCompilationStatistics(foundFunctionCount, definedFunctions.size, compiledFunctions.size, parseTime, compileTime, classLoadTime, handleLoadTime, totalPreoptimizedInstructions, totalOptimizedInstructions)

        FunctionCompilerReporter.sendReports()
    }

    private fun defineFunction(funcId: DataLocation, fieldStorage: ICodeFieldStorage, instructions: List<Instruction>, commandCount: Int, compiledFunctions: Set<DataLocation>): Class<*> {
        val className = "function/${funcId.namespace}/${funcId.path}".replace('.', '$')

        val cw = createClassWriter()
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run",
            "(${commandSourceStackClass.descriptorString()})I", null, null)
        mv.visitCode()
        val codeWriter = CodeWriter(mv)
        codeWriter.defineLocal("static\$CommandSourceStack", commandSourceStackClass) {
            EmitterContext(fieldStorage, codeWriter, compiledFunctions, mapOf(), mapOf(), Instructions2.root().copy(children = instructions.toMutableList()), mutableMapOf(), mutableMapOf()).run()
        }

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