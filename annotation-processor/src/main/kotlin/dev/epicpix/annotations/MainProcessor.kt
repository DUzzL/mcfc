package dev.epicpix.annotations

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Origin
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.epicpix.annotations.commands.generateCommandsDataReader
import dev.epicpix.annotations.commands.parseCommandsData
import dev.epicpix.annotations.instructions.*
import dev.epicpix.annotations.tests.generateTests
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.constant.ConstantDescs
import java.lang.constant.MethodTypeDesc
import java.nio.file.Files
import java.util.jar.JarFile

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

data class DefinitionPriorityTree(val def: ParsedTokenInstructionDefinition, val backwardConnections: MutableList<DefinitionPriorityTree> = mutableListOf()) {
    fun length(): Int {
        if(backwardConnections.isEmpty()) return 1
        return 1 + backwardConnections.maxOf { it.length() }
    }

    fun getAll(): List<DefinitionPriorityTree> {
        if(backwardConnections.isEmpty()) return listOf(this)
        return backwardConnections.fold(listOf(this)) { acc, v -> acc + v.getAll() }
    }
}

private lateinit var dataDir: String

fun loadJson(file: String): Map<String, JsonElement> {
    return GsonBuilder().create().fromJson(loadDataFile("$file.json"), JsonObject::class.java).asMap()
}

fun loadDataFile(file: String): String {
    return Files.readString(File(dataDir, file).toPath())
}

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: ProcessorOptions,
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true
        dataDir = options.dataDir

        setJars(options.jars?.map { JarFile(File(it)) } ?: emptyList())
        try {
            setResolver(resolver)
            logger.info("Running KSP for ${options.project} - ${options.sourceSet}")
            if(options.sourceSet == "main") {
                generateInstructionData(resolver)
                val targets = mutableListOf<String>()
                val file = parseCode(loadDataFile("data.instructions"))

                if(options.project == "common" || options.project == "minecraft") {
                    val emitterClass = file.headers["emitter_${options.project}_file"]!!
                    val emitterPackage = emitterClass.substring(0, emitterClass.lastIndexOf('.'))
                    val definitions = loadJson(options.project)
                    if(definitions["definition_file"] != null) {
                        generateFunctionMapping(definitions, options.minecraftVersion, "$emitterPackage.EmitterTargets", targets)
                    }
                }

                when(options.project) {
                    "common" -> {
                        generateInstructionData(file, true, options.minecraftVersion, targets)
                        generateConstantsData()
                        generateCommandsData()
                    }
                    "common-test" -> {
                        generateTests(codeGenerator, options.tests!!)
                    }
                    "minecraft" -> {
                        generateInstructionData(file, false, options.minecraftVersion, targets)
                    }
                }
            }
        } finally {
            closeClassInfo()
        }
        return listOf()
    }

    fun generateConstantsData() {
        val resultPackage = "dev.epicpix.minecraftfunctioncompiler"
        val resultName = "CompileConstants"
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("public final class $resultName {\n")
        map.appendText("    private $resultName() {}\n")
        // Resolved at runtime: Loom dev runs set -Dfabric.development=true; must not be a KSP option (it would invalidate KSP outputs per task graph)
        map.appendText("    public static final boolean isDevelopmentEnvironment = Boolean.getBoolean(\"fabric.development\");\n")
        map.appendText("    public static final String versionCommit = \"${options.commit}\";\n")
        map.appendText("    public static final String versionName = \"${options.version}\";\n")
        map.appendText("}\n")
        map.close()
    }

    fun generateCommandsData() {
        val data = parseCommandsData(loadDataFile("vanilla.commands"))
        val (cmdStr, cmdTypes) = generateCommandsDataReader(data)
        run {
            val resultPackage = "dev.epicpix.minecraftfunctioncompiler.commands"
            val resultName = "CommandParser"
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
            map.appendText("package $resultPackage;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.data.*;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.diagnostics.*;\n")
            map.appendText("import java.util.*;\n")
            map.appendText("public final class $resultName {\n")
            map.appendText("    private $resultName() {}\n")
            map.appendText(cmdStr.substringBeforeLast('\n').prependIndent("    ") + "\n")
            map.appendText("}\n")
            map.close()
        }
        run {
            val names = mutableListOf<String>()

            for((kind, types) in cmdTypes) {
                val paramsClassName = "_COMMAND_$kind".snakeToLowerCamelCase()
                val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.commands.params", paramsClassName, "java")
                names.add("dev.epicpix.minecraftfunctioncompiler.commands.params.$paramsClassName")
                map.appendText("package dev.epicpix.minecraftfunctioncompiler.commands.params;\n")
                map.appendText("public record $paramsClassName(\n${types.joinToString(",\n") { (name, type) -> "    $type ${name.snakeToLowerCamelCase()}" }}\n) implements dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams {}\n")
                map.close()
            }

            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.commands.params", "CommandParams", "java")
            map.appendText("package dev.epicpix.minecraftfunctioncompiler.commands.params;\n")
            map.appendText("public sealed interface CommandParams permits\n")
            names.add("dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFallbackVanilla")
            var i = 0
            for(name in names) {
                if(++i == names.size) {
                    map.appendText("    $name\n")
                }else {
                    map.appendText("    $name,\n")
                }
            }
            map.appendText("{}\n")
            map.close()

            val map2 = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.commands.params", "package-info", "java")
            map2.appendText("@org.jspecify.annotations.NullMarked\n")
            map2.appendText("package dev.epicpix.minecraftfunctioncompiler.commands.params;\n")
            map2.close()

        }
    }

    private fun getTarget(target: String, targets: MutableList<String>): Int {
        var index = targets.indexOf(target)
        if(index == -1) {
            index = targets.size
            targets.add(target)
        }
        return index
    }

    fun generateFunctionMapping(data: Map<String, JsonElement>, minecraftVersion: MinecraftVersion?, emitterTargetsClass: String, targets: MutableList<String>) {
        val resultClass = data["definition_file"]!!.asString
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.') + 1)
        val mapExtensions = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        mapExtensions.appendText("package $resultPackage;\n")
        mapExtensions.appendText("import static $emitterTargetsClass.TARGETS;\n")
        mapExtensions.appendText("public final class $resultName {\n")
        mapExtensions.appendText("    private $resultName() {}\n")
        fun getForCurrentVersion(v: JsonElement?): String? {
            if(v?.isJsonObject == true) {
                val v = v.asJsonObject!!.asMap()
                v.forEach { (sk, v) ->
                    for(k in sk.split(",")) {
                        val v = v.asString!!
                        val cVersion = MinecraftVersion.parse(k)
                        if(cVersion == minecraftVersion) return v
                    }
                }
                return null
            } else {
                return v?.asString!!
            }
        }
        data["definitions_functions"]?.asJsonObject?.asMap()?.forEach { (k, v) ->
            val content = getForCurrentVersion(v)
            if(content != null) {
                val fullDescriptor = content.split("@")[0]
                val className = fullDescriptor.split(":")[0]
                val methodDescriptor = MethodTypeDesc.ofDescriptor("(" + fullDescriptor.split("(")[1])
                val methodName = fullDescriptor.split(":")[1].split("(")[0]
                val classInfo = loadClassInfo(className)
                val methodInfo = classInfo.methods.find { f -> f.name == methodName && f.descriptor == methodDescriptor }?.checkPublic() ?: throw IllegalArgumentException("Failed to find method $className:$methodName${methodDescriptor.descriptorString()}")

                val callDetails = content.split("@").getOrElse(1) { "" }
                val noReorder = callDetails.contains("N")
                val target = "new InstructionTarget.FunctionTarget(${boxClassName(className)}, \"$methodName\", ${boxMethodDescriptor(methodDescriptor)}, ${methodInfo.isStatic}, ${classInfo.isInterface}, ${methodName == "<init>"}, ${noReorder})"
                val args = mutableListOf<String>()
                if(!methodInfo.isStatic) args.add("self")
                for(arg in 0..<methodDescriptor.parameterCount()) {
                    args.add("r$arg")
                }

                if(methodDescriptor.returnType() == ConstantDescs.CD_void) {
                    mapExtensions.appendText("    public static void call$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter${args.joinToString("") { ", dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue $it" }}) { codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}]${args.joinToString("") { ", $it" }}); }\n")
                } else {
                    mapExtensions.appendText("    public static dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister call$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter${args.joinToString("") { ", dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue $it" }}) { return codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}]${args.joinToString("") { ", $it" }}); }\n")
                }
            }
        }
        data["definitions_fields"]?.asJsonObject?.asMap()?.forEach { (k, v) ->
            val content = getForCurrentVersion(v)
            if(content != null) {
                val fullDescriptor = content.split("@")[0]
                val className = fullDescriptor.split(":")[0]
                val fieldName = fullDescriptor.split(":")[1]
                val fieldDescriptor = fullDescriptor.split(":")[2]
                val fieldInfo = loadClassInfo(className).fields.find { f -> f.name == fieldName && f.descriptor == fieldDescriptor }?.checkPublic() ?: throw IllegalArgumentException("Failed to find field $className:$fieldName:$fieldDescriptor")

                val callDetails = content.split("@").getOrNull(1)
                if(callDetails != null) {
                    val isStatic = fieldInfo.isStatic
                    if(callDetails.contains("R")) {
                        val target = "new InstructionTarget.FieldTarget(${boxClassName(className)}, \"$fieldName\", ${boxDescriptor(fieldDescriptor)}, ${isStatic}, false)"
                        if(isStatic) {
                            mapExtensions.appendText("    public static dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister load$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { return codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}]); }\n")
                        } else {
                            mapExtensions.appendText("    public static dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister load$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter, dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue self) { return codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}], self); }\n")
                        }
                    }
                    if(callDetails.contains("W")) {
                        if(fieldInfo.isFinal) {
                            throw IllegalArgumentException("Cannot generate a field write definition for $className:$fieldName:$fieldDescriptor because it is final")
                        }
                        val target = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FieldTarget(${boxClassName(className)}, \"$fieldName\", ${boxDescriptor(fieldDescriptor)}, ${isStatic}, true)"
                        if(isStatic) {
                            mapExtensions.appendText("    public static void store$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter, dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue value) { codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}], value); }\n")
                        } else {
                            mapExtensions.appendText("    public static void store$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter, dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue self, dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue value) { codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}], self, value); }\n")
                        }
                    }
                }
            }
        }
        mapExtensions.appendText("}\n")
        mapExtensions.close()
    }

    fun generateInstructionData(file: ParsedFile, common: Boolean, minecraftVersion: MinecraftVersion?, targets: MutableList<String>) {
        val resultClass = if(common) file.headers["emitter_common_file"]!! else file.headers["emitter_minecraft_file"]!!
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.') + 1)
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("import java.lang.constant.ClassDesc;\n")
        map.appendText("import java.lang.constant.ConstantDescs;\n")
        map.appendText("import java.lang.constant.MethodTypeDesc;\n")
        map.appendText("import static ${resultPackage}.EmitterTargets.TARGETS;\n")
        map.appendText("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n")
        map.appendText("public class $resultName {\n")

        val info = mutableMapOf<String, List<AccessorStatistics>>()
        val enumFile = file.headers["enum_file"]!!

        // Collect all matching definitions with their generated code
        data class DefinitionChunkEntry(
            val name: String,
            val generatedCode: String,
            val writeReturn: Boolean,
            val stats: List<AccessorStatistics>,
        )
        val allEntries = mutableListOf<DefinitionChunkEntry>()
        file.definitions.forEach {
            if(it.usesObf != common) {
                val matchesVersion = if(it.versionRange == null) true else {
                    if(minecraftVersion !== null) {
                        val matchesFrom = it.versionRange.first?.let { v -> minecraftVersion >= v } ?: true
                        val matchesTo = it.versionRange.second?.let { v -> minecraftVersion <= v } ?: true
                        matchesFrom && matchesTo
                    }else {
                        false
                    }
                }
                if(matchesVersion) {
                    val output = ByteArrayOutputStream()
                    val codeGenResult = generateCode(it, file, output, minecraftVersion, targets)
                    info[it.name] = codeGenResult.first
                    allEntries.add(DefinitionChunkEntry(
                        name = it.name,
                        generatedCode = output.toByteArray().decodeToString(),
                        writeReturn = codeGenResult.second,
                        stats = codeGenResult.first,
                    ))
                }
            }
        }
        allEntries.groupBy { it.name.uppercase() }.filter { it.value.size > 1 }.keys.firstOrNull()?.let {
            throw IllegalArgumentException("Instruction $it has several definitions matching Minecraft version $minecraftVersion")
        }

        // Dispatch: InstructionType.id() -> 1-based local index (0 = not handled here), then a switch per chunk.
        // Ids are assigned at runtime, so the table is built in the static initializer.
        map.appendText("    private static final int[] LOCAL_INDEX = buildLocalIndex();\n")
        map.appendText("    private static int[] buildLocalIndex() {\n")
        map.appendText("        dev.epicpix.minecraftfunctioncompiler.il.InstructionType[] types = {\n")
        for(entry in allEntries) {
            map.appendText("            $enumFile.${entry.name.uppercase()},\n")
        }
        map.appendText("        };\n")
        map.appendText("        int max = 0;\n")
        map.appendText("        for(var t : types) max = Math.max(max, t.id());\n")
        map.appendText("        int[] result = new int[max + 1];\n")
        map.appendText("        for(int i = 0; i < types.length; i++) result[types[i].id()] = i + 1;\n")
        map.appendText("        return result;\n")
        map.appendText("    }\n")

        // Split definitions into chunks to avoid JVM method size limit (64KB bytecode)
        val maxChunkSize = 15
        val chunks = allEntries.chunked(maxChunkSize)

        // Generate a private static method for each chunk
        for((chunkIndex, chunk) in chunks.withIndex()) {
            map.appendText("    private static boolean emitChunk$chunkIndex(dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext context, dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter cw, int local) {\n")
            map.appendText("        switch(local) {\n")
            for((entryIndex, entry) in chunk.withIndex()) {
                map.appendText("        case ${chunkIndex * maxChunkSize + entryIndex + 1} -> { // ${entry.name.uppercase()}\n")
                map.appendText(entry.generatedCode.trimEnd().prependIndent("            "))
                map.appendText("\n")
                if(entry.writeReturn) {
                    map.appendText("            return true;\n")
                }
                map.appendText("        }\n")
            }
            map.appendText("        default -> {}\n")
            map.appendText("        }\n")
            map.appendText("        return false;\n")
            map.appendText("    }\n")
        }

        // Generate the main registration method that delegates to chunk methods
        map.appendText("    public static void registerInstructionEmitters() {\n")
        map.appendText("        dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions.registerEmitterInstruction((context) -> {\n")
        map.appendText("            var cw = context.codeWriter();\n")
        map.appendText("            int id = context.instruction().type().id();\n")
        map.appendText("            int local = id < LOCAL_INDEX.length ? LOCAL_INDEX[id] : 0;\n")
        map.appendText("            return switch((local - 1) / $maxChunkSize) {\n")
        for(chunkIndex in chunks.indices) {
            map.appendText("                case $chunkIndex -> local != 0 && emitChunk$chunkIndex(context, cw, local);\n")
        }
        map.appendText("                default -> false;\n")
        map.appendText("            };\n")
        map.appendText("        });\n")
        map.appendText("    }\n")
        map.appendText("}\n")

        val mapTargets = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, "EmitterTargets", "java")
        mapTargets.appendText("package $resultPackage;\n")
        mapTargets.appendText("import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;\n")
        mapTargets.appendText("import java.lang.constant.ClassDesc;\n")
        mapTargets.appendText("import java.lang.constant.ConstantDescs;\n")
        mapTargets.appendText("import java.lang.constant.MethodTypeDesc;\n")
        mapTargets.appendText("public class EmitterTargets {\n")
        mapTargets.appendText("    public static final InstructionTarget[] TARGETS = new InstructionTarget[${targets.size}];\n")

        // Split static initializer into chunk methods to avoid JVM method size limit
        val targetChunkSize = 30
        val targetChunks = targets.chunked(targetChunkSize)
        for((chunkIdx, chunk) in targetChunks.withIndex()) {
            val startIndex = chunkIdx * targetChunkSize
            mapTargets.appendText("    private static void initTargets$chunkIdx() {\n")
            for((i, target) in chunk.withIndex()) {
                mapTargets.appendText("        TARGETS[${startIndex + i}] = $target;\n")
            }
            mapTargets.appendText("    }\n")
        }
        mapTargets.appendText("    static {\n")
        for(chunkIdx in targetChunks.indices) {
            mapTargets.appendText("        initTargets$chunkIdx();\n")
        }
        mapTargets.appendText("    }\n")
        mapTargets.appendText("}\n")

        if(!common) return

        val generatorClass = file.headers["generator_file"]!!
        val generatorResultPackage = generatorClass.substring(0, generatorClass.lastIndexOf('.'))
        val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.') + 1)
        val enumClass = file.headers["enum_file"]!!
        val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
        val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.') + 1)

        val impl = codeGenerator.createNewFile(Dependencies.ALL_FILES, generatorResultPackage, generatorResultClass, "java")
        impl.appendText("package $generatorResultPackage;\n")
        impl.appendText("import java.util.*;\n")
        impl.appendText("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n")
        impl.appendText("public final class $generatorResultClass {\n")

        val types = codeGenerator.createNewFile(Dependencies.ALL_FILES, enumResultPackage, enumResultClass, "java")
        types.appendText("package $enumResultPackage;\n")
        types.appendText("public class $enumResultClass {\n")
        val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"

        validateDefinitions(file)

        val writtenDefinitions = mutableListOf<String>()
        file.definitions.forEach {
            val named = it.name.uppercase()
            if(writtenDefinitions.contains(named)) {
                return@forEach
            }
            writtenDefinitions.add(named)
            val argNames = mutableListOf<String>()
            for((accessor, _) in it.params) {
                when(accessor.scope) {
                    ParsedTokenValueAccessorScope.RETURN -> argNames.add(accessor.name)
                    ParsedTokenValueAccessorScope.CONTEXT -> argNames.add(accessor.name)
                    ParsedTokenValueAccessorScope.INSTRUCTION -> argNames.add(accessor.name)
                    ParsedTokenValueAccessorScope.LOCAL -> continue
                    ParsedTokenValueAccessorScope.STATIC -> continue
                }
            }
            types.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.il.InstructionType $named = new dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\", new String[] {${argNames.joinToString { "\"$it\"" }}}${if(it.instructionFlags.isEmpty()) "" else ", ${it.instructionFlags.joinToString(" | ") { "dev.epicpix.minecraftfunctioncompiler.il.InstructionType.$it" }}"});\n")
            if(named.startsWith("STATIC_")) {
                return@forEach
            }
            val generics = StringBuilder()
            if(it.params.any { it.second is ParsedTokenExpressionTypeGeneric }) {
                generics.append(" <")
                val definedNames = mutableSetOf<String>()
                for(i in it.params.indices) {
                    val (_, type) = it.params[i]
                    if(type is ParsedTokenExpressionTypeGeneric) {
                        if(!definedNames.add(type.generic.name)) continue
                        if(generics.length != 2) {
                            generics.append(", ")
                        }
                        generics.append("${type.generic.name} extends ${javaToMinecraftGenericType(type.lowerBound.displayName, type.lowerBound.generics.map { javaToMinecraftGenericType(it.displayName) })}")
                    }
                }
                generics.append(">")
            }
            impl.appendText("public static$generics $instructionType ${named.snakeToLowerCamelCase()}(")
            for(i in it.params.indices) {
                val (param, type) = it.params[i]
                val typeName = when(param.scope) {
                    ParsedTokenValueAccessorScope.CONTEXT, ParsedTokenValueAccessorScope.RETURN -> {
                        if(type == ParsedTokenExpressionSimpleType.LABEL) {
                            "dev.epicpix.minecraftfunctioncompiler.il.LabelData"
                        } else {
                            "dev.epicpix.minecraftfunctioncompiler.il.LocationData<${
                                when(type) {
                                    ParsedTokenExpressionSimpleType.INTEGER -> "Integer"
                                    ParsedTokenExpressionSimpleType.BOOLEAN -> "Boolean"
                                    ParsedTokenExpressionSimpleType.DOUBLE -> "Double"
                                    ParsedTokenExpressionSimpleType.FLOAT -> "Float"
                                    ParsedTokenExpressionSimpleType.LONG -> "Long"
                                    is ParsedTokenExpressionTypeComplex -> {
                                        if(param.scope == ParsedTokenValueAccessorScope.RETURN) {
                                            javaToMinecraftGenericType(type.displayName, type.generics.map { javaToMinecraftGenericType(it.displayName) })
                                        } else {
                                            "? extends ${javaToMinecraftGenericType(type.displayName, type.generics.map { javaToMinecraftGenericType(it.displayName) })}"
                                        }
                                    }
                                    is ParsedTokenExpressionTypeGeneric -> (if(type.generic.allowExtends) "? extends " else "") + type.generic.name
                                    else -> TODO("Unknown type: $type")
                                }
                            }>"
                        }
                    }

                    ParsedTokenValueAccessorScope.INSTRUCTION -> {
                        when(type) {
                            ParsedTokenExpressionSimpleType.INTEGER -> "int"
                            ParsedTokenExpressionSimpleType.BOOLEAN -> "boolean"
                            ParsedTokenExpressionSimpleType.DOUBLE -> "double"
                            ParsedTokenExpressionSimpleType.FLOAT -> "float"
                            ParsedTokenExpressionSimpleType.LONG -> "long"
                            is ParsedTokenExpressionTypeComplex -> kotlinToJavaType(type.displayName, false, type.generics.map { javaToMinecraftGenericType(it.displayName) })
                            else -> TODO("$type")
                        }
                    }

                    ParsedTokenValueAccessorScope.LOCAL -> continue
                    ParsedTokenValueAccessorScope.STATIC -> continue
                }

                impl.appendText("$typeName ${param.name.snakeToLowerCamelCase()}")
                if(i < it.params.size - 1) {
                    impl.appendText(", ")
                }
            }
            impl.appendText(") {")
            impl.appendText("return new $instructionType($enumClass.$named, new Object[] {${it.params.filter { it.first.scope == ParsedTokenValueAccessorScope.INSTRUCTION || it.first.scope == ParsedTokenValueAccessorScope.CONTEXT || it.first.scope == ParsedTokenValueAccessorScope.RETURN }.joinToString { "${it.first.name.snakeToLowerCamelCase()}" }}}); }\n")
        }

        val defs = mutableListOf<DefinitionPriorityTree>()
        file.definitions.filter { def -> def.name.startsWith("static_") }.forEach {
            defs.add(DefinitionPriorityTree(it))
        }
        defs.forEach { tree ->
            val it = tree.def
            val staticParams = it.params.filter { (param, _) -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" }
            for(param in staticParams) {
                val checkName = "static${param.first.name}".toUpperSnakeCase()
                if(it.name.uppercase() != checkName) {
                    tree.backwardConnections.add(defs.find { treeDef -> treeDef.def.name.uppercase() == checkName }
                        ?: throw NullPointerException("Definition with name '$checkName' not found"))
                }
            }
        }
        val instructionEnumType = "dev.epicpix.minecraftfunctioncompiler.il.InstructionType"
        impl.appendText("\n    public static List<$instructionType> addStaticRequirements(List<$instructionType> instructions) {\n")
        impl.appendText("        HashSet<$instructionEnumType> requirementInstructions = new HashSet<>();\n")
        impl.appendText("        for(var instruction : instructions) addStaticRequirementsInternal(instruction, requirementInstructions);\n")
        impl.appendText("        ArrayList<Instruction> resultInstructions = new ArrayList<>(instructions);\n")
        val defPriorities = defs.map { it.def to it.length() }.sortedByDescending { it.second }
        for((def, _) in defPriorities) {
            impl.appendText("        if(requirementInstructions.contains($enumClass.${def.name.uppercase()})) resultInstructions.add(0, new $instructionType($enumClass.${def.name.uppercase()}, new Object[0]));\n")
        }
        impl.appendText("        return resultInstructions;\n")
        impl.appendText("    }\n")
        impl.appendText("    private static void addStaticRequirementsInternal($instructionType instruction, HashSet<$instructionEnumType> mutableSet) {\n")
        impl.appendText("        int id = instruction.type().id();\n")
        impl.appendText("        if(id >= STATIC_REQS.length) return;\n")
        impl.appendText("        var reqs = STATIC_REQS[id];\n")
        impl.appendText("        if(reqs != null) Collections.addAll(mutableSet, reqs);\n")
        impl.appendText("    }\n")
        // Indexed by InstructionType.id(); ids are assigned at runtime, so the table is built in the static initializer
        impl.appendText("    private static final $instructionEnumType[][] STATIC_REQS = buildStaticRequirements();\n")
        impl.appendText("    private static $instructionEnumType[][] buildStaticRequirements() {\n")
        impl.appendText("        var byType = new HashMap<$instructionEnumType, $instructionEnumType[]>();\n")
        file.definitions.distinctBy { it.name.uppercase() }.forEach {
            val staticParams = it.params.filter { (param, _) -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" && it.name.uppercase() != "static${param.name}".toUpperSnakeCase() }
            if(staticParams.isNotEmpty()) {
                val reqs = LinkedHashSet<String>()
                for(param in staticParams) {
                    val paramName = "static${param.first.name}".toUpperSnakeCase()
                    defs.find { it.def.name.uppercase() == paramName }!!.getAll().forEach { req -> reqs.add(req.def.name.uppercase()) }
                }
                impl.appendText("        byType.put($enumClass.${it.name.uppercase()}, new $instructionEnumType[] {${reqs.joinToString { req -> "$enumClass.$req" }}});\n")
            }
        }
        impl.appendText("        int max = 0;\n")
        impl.appendText("        for(var t : byType.keySet()) max = Math.max(max, t.id());\n")
        impl.appendText("        var result = new $instructionEnumType[max + 1][];\n")
        impl.appendText("        for(var e : byType.entrySet()) result[e.getKey().id()] = e.getValue();\n")
        impl.appendText("        return result;\n")
        impl.appendText("    }\n")

        impl.appendText("}\n")
        impl.close()
        types.appendText("}\n")
        types.close()
    }

    @OptIn(KspExperimental::class)
    fun generateInstructionData(resolver: Resolver): Map<String, String> {
        val instructionMappings = mutableMapOf<String, String>()
        val classesList = resolver.getSymbolsWithAnnotation(InstructionSourceClass::class.java.name).toList()
        classesList.forEach {
            val annotation = it.getAnnotationsByType(InstructionSourceClass::class).first()
            val generatorClass = annotation.generatorClass
            val generatorResultPackage = generatorClass.substring(0, generatorClass.lastIndexOf('.'))
            val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.') + 1)
            val enumClass = annotation.enumClass
            val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
            val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.') + 1)

            val impl = codeGenerator.createNewFile(Dependencies(true, (it as KSClassDeclaration).containingFile!!), generatorResultPackage, generatorResultClass, "java")
            impl.appendText("package $generatorResultPackage;\n")
            impl.appendText("import java.util.*;\n")
            impl.appendText("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n")
            impl.appendText("public final class $generatorResultClass {\n")

            val types = codeGenerator.createNewFile(Dependencies(true, it.containingFile!!), enumResultPackage, enumResultClass, "java")
            types.appendText("package $enumResultPackage;\n")
            types.appendText("public class $enumResultClass {\n")

            classesList.forEach {
                val defs = (it as KSClassDeclaration).getDeclaredFunctions()
                for(def in defs) {
                    if(def.origin != Origin.SYNTHETIC) {
                        val named = def.simpleName.asString().toUpperSnakeCase()
                        types.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.il.InstructionType $named = new dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\", new String[] {${def.parameters.joinToString { "\"${it.name!!.asString()}\"" }}});\n")
                        instructionMappings[named] = "$enumClass.$named"
                        val params = mutableListOf<String>()
                        val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"
                        impl.appendText("public static $instructionType ${def.simpleName.asString()}(")
                        for(i in def.parameters.indices) {
                            val param = def.parameters[i]
                            val resolvedType = param.type.resolve()
                            val typeName = kotlinToJavaType(resolvedType.declaration.qualifiedName!!.asString(), false)
                            val typeArgs = resolvedType.arguments.joinToString { arg ->
                                "? extends " + (arg.type?.resolve()?.declaration?.qualifiedName?.asString()?.run {
                                    kotlinToJavaType(this, true)
                                } ?: "Object")
                            }

                            val fullTypeName = if(typeArgs.isNotEmpty()) "$typeName<$typeArgs>" else typeName
                            impl.appendText("$fullTypeName ${param.name!!.asString()}")
                            if(i < def.parameters.size - 1) {
                                impl.appendText(", ")
                            }
                            params.add(param.name!!.asString())
                        }
                        impl.appendText(") {")
                        impl.appendText("return new $instructionType($enumClass.$named, new Object[] {${params.joinToString()}}); }\n")
                    }
                }
            }

            impl.appendText("}\n")
            impl.close()
            types.appendText("}\n")
            types.close()
        }
        return instructionMappings
    }
}

/**
 * Versioned variants of one instruction share a single InstructionType/factory, so their parameters and flags must match.
 * (Jump edges need no flag: the optimizer's CFG treats every label operand of a non-LABEL instruction as an edge.)
 */
private fun validateDefinitions(file: ParsedFile) {
    for((name, variants) in file.definitions.groupBy { it.name.uppercase() }) {
        val first = variants.first()
        for(other in variants.drop(1)) {
            if(other.params != first.params || other.instructionFlags.toSet() != first.instructionFlags.toSet()) {
                throw IllegalArgumentException("Versioned variants of instruction $name must have identical parameters and flags")
            }
        }
    }
}

fun String.toUpperSnakeCase() = split(Regex("(?=[A-Z])")).joinToString("_").uppercase()
fun String.snakeToLowerCamelCase() = Regex("_[a-zA-Z]").replace(lowercase()) { it.value.replace("_", "").uppercase() }

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.logger, ProcessorOptions.fromMap(environment.options))
    }
}