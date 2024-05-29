package dev.epicpix.annotations

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Origin
import dev.epicpix.annotations.commands.generateCommandsDataReader
import dev.epicpix.annotations.commands.parseCommandsData
import dev.epicpix.annotations.instructions.*
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import net.fabricmc.accesswidener.AccessWidenerWriter
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyUtils
import org.objectweb.asm.Type
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

data class MappingData(
    val mappedClasses: MutableMap<String, String> = mutableMapOf(),
    val mappedMethods: MutableMap<IMappingProvider.Member, String> = mutableMapOf(),
    val mappedFields: MutableMap<IMappingProvider.Member, String> = mutableMapOf(),
)

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

fun MappingData?.getClassName(name: String): String {
    if(this == null) return name
    return mappedClasses[name] ?: (if(!name.startsWith("net/minecraft")) name else error("No class mapping for $name"))
}
fun MappingData?.getMethodName(owner: String, name: String, desc: String): String {
    if(this == null) return name
    return mappedMethods.entries.find { it.key.name == name && it.key.owner == owner && it.key.desc == desc }?.value ?: (if(!owner.startsWith("net/minecraft")) name else error("No method mapping for $owner.$name $desc"))
}
fun MappingData?.getFieldName(owner: String, name: String, desc: String): String {
    if(this == null) return name
    return mappedFields.entries.find { it.key.name == name && it.key.owner == owner && it.key.desc == desc }?.value ?: (if(!owner.startsWith("net/minecraft")) name else error("No field mapping for $owner.$name $desc"))
}

fun loadToml(file: String): Map<String, Any> {
    val str = loadDataFile("$file.toml") ?: throw NullPointerException("Failed to read file $file.toml")
    return tomlMapper {  }.decode(str)
}

fun loadDataFile(file: String): String? {
    return MainProcessor::class.java.getResourceAsStream("/data/$file")?.readAllBytes()?.decodeToString()
}

fun remapType(type: Type, mappingData: MappingData?): Type {
    if(mappingData == null) return type
    if(type.sort == Type.METHOD) {
        val params = type.argumentTypes.map { remapType(it, mappingData) }
        return Type.getMethodType(type.returnType.run { remapType(this, mappingData) }, *params.toTypedArray())
    }
    if(type.sort != Type.OBJECT) return type
    return Type.getType("L" + (mappingData.getClassName(type.descriptor!!.run { substring(1, length - 1) })) + ";")
}

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true

        val mappingData = if(options["isJarTask"] == "true" && options["mappingsFile"] != null) MappingData() else null
        if(mappingData != null) {
            val mappingsPath = Paths.get(options["mappingsFile"]!!)
            val mappingProvider = TinyUtils.createTinyMappingProvider(mappingsPath, "named", "intermediary")
            mappingProvider.load(object : IMappingProvider.MappingAcceptor {
                override fun acceptClass(srcName: String, dstName: String) { mappingData.mappedClasses[srcName] = dstName }
                override fun acceptMethod(method: IMappingProvider.Member, dstName: String) { mappingData.mappedMethods[method] = dstName }
                override fun acceptField(field: IMappingProvider.Member, dstName: String) { mappingData.mappedFields[field] = dstName }
                override fun acceptMethodArg(method: IMappingProvider.Member?, lvIndex: Int, dstName: String?) {}
                override fun acceptMethodVar(method: IMappingProvider.Member?, lvIndex: Int, startOpIdx: Int, asmIndex: Int, dstName: String?) {}
            })
        }
        val jarFiles = mutableListOf<JarFile>()
        val jarFileMappings = mutableMapOf<String, Int>()
        if(options["jars"] != null) {
            val jars = options["jars"]!!.split(":")
            for(jarName in jars) {
                val jar = JarFile(File(jarName))
                val index = jarFiles.size
                jarFiles.add(jar)
                for(entry in jar.entries()) {
                    if(!entry.isDirectory) {
                        jarFileMappings[entry.name] = index
                    }
                }
            }
            setJarInfo(jarFiles to jarFileMappings)
        }
        setResolver(resolver)
        val packageMinecraftVersion = options["minecraft_version"]?.replace(".", "_")?.run { "v$this" }
        val minecraftVersion = options["minecraft_version"]?.split(".")
        val definitions = options["definitions"]?.let { loadToml(it) }
        generateInstructionData(resolver)
        if(definitions != null) {
            if(definitions["definition_file"] != null) {
                generateFunctionMapping(mappingData, definitions, minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0, packageMinecraftVersion)
            }
        }
        if(options["definitions"] != null) {
            generateInstructionData(mappingData, options["definitions"] == "common", minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0, packageMinecraftVersion)
        }
        if(options["definitions"] == "common") {
            generateConstantsData()
            generateCommandsData()
        }
        if(packageMinecraftVersion != null && mappingData != null) {
            val accessWidenerWriter = AccessWidenerWriter()
            AccessWidenerReader(object : AccessWidenerVisitor {
                override fun visitHeader(namespace: String?) {
                    accessWidenerWriter.visitHeader("intermediary")
                }

                override fun visitClass(name: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                    accessWidenerWriter.visitClass(mappingData.getClassName(name), access, transitive)
                }

                override fun visitMethod(owner: String, name: String, descriptor: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                    accessWidenerWriter.visitMethod(mappingData.getClassName(owner), mappingData.getMethodName(owner, name, descriptor), remapType(Type.getMethodType(descriptor), mappingData).descriptor, access, transitive)
                }

                override fun visitField(owner: String, name: String, descriptor: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                    accessWidenerWriter.visitField(mappingData.getClassName(owner), mappingData.getFieldName(owner, name, descriptor), remapType(Type.getType(descriptor), mappingData).descriptor, access, transitive)
                }
            }).read(Files.readAllBytes(Paths.get(options["accesswidener"]!!)))
            val generatedAccessWidener = codeGenerator.createNewFile(Dependencies.ALL_FILES, "", "minecraftfunctioncompiler.$packageMinecraftVersion.generated", "accesswidener")
            generatedAccessWidener.write(accessWidenerWriter.write())
            generatedAccessWidener.close()
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
        map.appendText("    public static final boolean isDevelopmentEnvironment = ${options["isJarTask"] != "true"};\n")
        map.appendText("    public static final String versionCommit = \"${options["commit"] ?: "development"}\";\n")
        map.appendText("    public static final String versionName = \"${options["version"] ?: error("Version option not specified")}\";\n")
        map.appendText("}\n")
        map.close()
    }

    fun generateCommandsData() {
        val data = parseCommandsData(loadDataFile("data.commands")!!);
        run {
            val resultPackage = "dev.epicpix.minecraftfunctioncompiler.commands"
            val resultName = "CommandParser"
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
            map.appendText("package $resultPackage;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.data.*;\n")
            map.appendText("import java.util.*;\n")
            map.appendText("public final class $resultName {\n")
            map.appendText("    private $resultName() {}\n")
            map.appendText("    public static Command parseCommand(String command) {\n")
            map.appendText("        var reader = new StringCommandReader(command);\n")
            map.appendText(generateCommandsDataReader(data).substringBeforeLast('\n').prependIndent("        ") + "\n")
            map.appendText("    }\n")
            map.appendText("}\n")
            map.close()
        }
        run {
            val resultPackage = "dev.epicpix.minecraftfunctioncompiler.commands"
            val resultName = "CommandType"
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
            map.appendText("package $resultPackage;\n")
            map.appendText("public enum $resultName {\n")
            for((command) in data.commands) {
                map.appendText("$command,\n")
            }
            map.appendText("}\n")
            map.close()
        }
    }

    fun generateFunctionMapping(mappingData: MappingData?, data: Map<String, Any>, majorVer: Int, minorVer: Int, packageMinecraftVersion: String?) {
        val resultClass = (data["definition_file"] as String).replace("\$VERSION", packageMinecraftVersion ?: "common")
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
        val mapExtensions = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "kt")
        mapExtensions.appendText("package $resultPackage\n")
        fun findVersionSpecific(v: Any?): String? {
            if(v is Map<*, *>) {
                v.forEach { (k, v) ->
                    k as String
                    v as String
                    val minecraftVersion = k.split(".")
                    val cMajor = minecraftVersion[1].toInt()
                    val cMinor = minecraftVersion[2].filter { it.isDigit() }.toIntOrNull() ?: 0
                    if(cMajor == majorVer && cMinor == minorVer) return v
                    else if( k.endsWith("+") && ((majorVer == cMajor && minorVer >= cMinor) || (majorVer > cMajor))) {
                        return v
                    }else if(k.endsWith("-") && ((majorVer == cMajor && minorVer <= cMinor) || (majorVer < cMajor))) {
                        return v
                    }
                }
                return null
            }else {
                return v as String
            }
        }
        (data["definitions_functions"] as? Map<*, *>)?.forEach { (k, v) ->
            val content = findVersionSpecific(v)
            if(content != null) {
                val fullDescriptor = content.split("@")[0]
                val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                val methodDescriptor = remapType(Type.getMethodType("(" + fullDescriptor.split("(")[1]), mappingData).descriptor
                val methodName = mappingData.getMethodName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1].split("(")[0], "(" + fullDescriptor.split("(")[1])
                val callDetails = content.split("@").getOrNull(1)
                if(callDetails != null) {
                    val isStatic = !callDetails.contains("M")
                    val isInterface = callDetails.contains("I")
                    val opcode = if(isStatic) 184 else if(isInterface) 185 else 182
                    mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.call$k(params: () -> Unit) { params(); methodVisitor.visitMethodInsn($opcode, \"$className\", \"$methodName\", \"$methodDescriptor\", $isInterface); }\n".replace("\$", "\\$"))
                }
            }
        }
        (data["definitions_fields"] as? Map<*, *>)?.forEach { (k, v) ->
            val content = findVersionSpecific(v)
            if(content != null) {
                val fullDescriptor = content.split("@")[0]
                val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                val fieldDescriptor = remapType(Type.getType(fullDescriptor.split(":")[2]), mappingData).descriptor
                val fieldName = mappingData.getFieldName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1], fullDescriptor.split(":")[2])
                val callDetails = content.split("@").getOrNull(1)
                if(callDetails != null) {
                    val isStatic = !callDetails.contains("M")
                    if(isStatic) {
                        mapExtensions.appendText("@Suppress(\"NOTHING_TO_INLINE\") inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.load$k() { methodVisitor.visitFieldInsn(178, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n".replace("\$", "\\$"))
                        mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.store$k(value: () -> Unit) { value(); methodVisitor.visitFieldInsn(179, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n".replace("\$", "\\$"))
                    }else {
                        mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.load$k(params: () -> Unit) { params(); methodVisitor.visitFieldInsn(180, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n".replace("\$", "\\$"))
                        mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.store$k(params: () -> Unit) { params(); methodVisitor.visitFieldInsn(181, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n".replace("\$", "\\$"))
                    }
                }
            }
        }
    }

    fun generateInstructionData(mappingData: MappingData?, common: Boolean, majorVer: Int, minorVer: Int, packageMinecraftVersion: String?) {
        fun findVersionSpecific(v: Any?): String? {
            if(v is Map<*, *>) {
                v.forEach { (k, v) ->
                    k as String
                    v as String
                    val minecraftVersion = k.split(".")
                    val cMajor = minecraftVersion[1].toInt()
                    val cMinor = minecraftVersion[2].filter { it.isDigit() }.toIntOrNull() ?: 0
                    if(cMajor == majorVer && cMinor == minorVer) return v
                    else if( k.endsWith("+") && ((majorVer == cMajor && minorVer >= cMinor) || (majorVer > cMajor))) {
                        return v
                    }else if(k.endsWith("-") && ((majorVer == cMajor && minorVer <= cMinor) || (majorVer < cMajor))) {
                        return v
                    }
                }
                return null
            }else {
                return v as String
            }
        }

        val file = parseCode(loadDataFile("data.instructions")!!) {
            val content = findVersionSpecific(it)
            if(content != null) {
                val fullDescriptor = content.split("@")[0]
                val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                val methodDescriptor = remapType(Type.getMethodType("(" + fullDescriptor.split("(")[1]), mappingData).descriptor
                val methodName = mappingData.getMethodName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1].split("(")[0], "(" + fullDescriptor.split("(")[1])
                val callDetails = content.split("@").getOrNull(1)
                if(callDetails != null) {
                    val isStatic = !callDetails.contains("M")
                    val isInterface = callDetails.contains("I")
                    val opcode = if(isStatic) 184 else if(isInterface) 185 else 182
                    val obf = className.contains("net/minecraft") || methodDescriptor.contains("net/minecraft")
                    if(!common || !obf) {
                        return@parseCode "$className $methodName $methodDescriptor $opcode $obf $isInterface"
                    }
                }
            }
            return@parseCode null
        }

        val resultClass = (if(common) file.fileInfo["emitter_noobf_file"]!! else file.fileInfo["emitter_obf_file"]!!).replace("\$VERSION", packageMinecraftVersion ?: "common")
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("public class $resultName {\n")
        map.appendText("    public static void registerInstructionEmitters() {\n")
        map.appendText("        dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructionsKt.registerEmitterInstruction((context) -> {\n")
        map.appendText("            var cw = context.getCodeWriter();\n")
        map.appendText("            var mv = cw.getMethodVisitor();\n")
        map.appendText("            var type = context.getInstruction().type();\n")
        val info = mutableMapOf<String, List<AccessorStatistics>>()
        file.definitions.forEach {
            if(it.usesObf != common) {
                val matchesVersion = if(it.versionRange == null) true else {
                    val f = it.versionRange.first?.split(".")?.let {
                        val cMajor = it[1].toInt()
                        val cMinor = it.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                        (majorVer > cMajor) || (majorVer == cMajor && minorVer >= cMinor)
                    } ?: true
                    val t = it.versionRange.second?.split(".")?.let {
                        val cMajor = it[1].toInt()
                        val cMinor = it.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                        (majorVer < cMajor) || (majorVer == cMajor && minorVer <= cMinor)
                    } ?: true
                    f && t
                }
                if(matchesVersion) {
                    map.appendText("            if(type == ${file.fileInfo["enum_file"]}.${it.name.uppercase()}) {\n")
                    val output = ByteArrayOutputStream()
                    info[it.name] = generateCode(mappingData, it, file, output)
                    map.appendText(output.toByteArray().decodeToString().trimEnd().prependIndent("                "))
                    map.appendText("\n")
                    map.appendText("                return true;\n")
                    map.appendText("            }\n")
                }
            }
        }
        map.appendText("            return false;\n")
        map.appendText("        });\n")
        map.appendText("    }\n")
        map.appendText("}\n")

        if(common) {
            val generatorClass = file.fileInfo["generator_file"]!!
            val generatorResultPackage = generatorClass.substring(0, generatorClass.lastIndexOf('.'))
            val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.')+1)
            val enumClass = file.fileInfo["enum_file"]!!
            val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
            val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.')+1)

            val impl = codeGenerator.createNewFile(Dependencies.ALL_FILES, generatorResultPackage, generatorResultClass + "Impl", "kt")
            impl.appendText("package $generatorResultPackage\n")
            impl.appendText("data object $generatorResultClass {\n")

            val types = codeGenerator.createNewFile(Dependencies.ALL_FILES, enumResultPackage, enumResultClass, "java")
            types.appendText("package $enumResultPackage;\n")
            types.appendText("public class $enumResultClass {\n")
            val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"

            file.collapsableDefinitions.forEach {
                val named = it.name.uppercase()
                impl.appendText("fun ${named.snakeToLowerCamelCase()}(")
                for((param, type) in it.params) {
                    val typeName = when(param.scope) {
                        ParsedTokenValueAccessorScope.CONTEXT -> {
                            if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)) {
                                "dev.epicpix.minecraftfunctioncompiler.il.ScopeData"
                            }else {
                                "dev.epicpix.minecraftfunctioncompiler.il.LocationData"
                            }
                        }
                        ParsedTokenValueAccessorScope.INSTRUCTION -> {
                            if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)) {
                                "Int"
                            }else if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)) {
                                "Boolean"
                            }else if(type is ParsedTokenExpressionTypeComplex) {
                                type.typeName.run { if(startsWith("java.lang.")) substring(10) else this }
                            }else {
                                TODO()
                            }
                        }
                        ParsedTokenValueAccessorScope.LOCAL -> {
                            continue
                        }
                        ParsedTokenValueAccessorScope.STATIC -> {
                            continue
                        }
                    }

                    impl.appendText("${param.name.snakeToLowerCamelCase()}: $typeName, ")
                }
                impl.appendText("): $instructionType {\n")
                fun mapValues(to: ParsedTokenInstructionDefinition) {
                    impl.appendText(to.name.uppercase().snakeToLowerCamelCase())
                    impl.appendText("(")
                    for((value, _) in to.params) {
                        if(value.scope != ParsedTokenValueAccessorScope.STATIC) {
                            impl.appendText(value.name)
                            impl.appendText(", ")
                        }
                    }
                    impl.appendText(")")
                }
                for(cond in it.conditions) {
                    if(cond.checks.isEmpty()) {
                        impl.appendText("        return ")
                        mapValues(file.definitions.find { def -> def.name == cond.instruction }!!)
                        impl.appendText("\n")
                        continue
                    }
                    impl.appendText("        if(")
                    for(checkIndex in cond.checks.indices) {
                        val check = cond.checks[checkIndex]
                        impl.appendText(check.param.snakeToLowerCamelCase())
                        when(check.operator) {
                            ParsedTokenInstructionConditionOperator.EQ -> impl.appendText("==")
                            ParsedTokenInstructionConditionOperator.NE -> impl.appendText("!=")
                        }
                        val param = it.params.find { (param, _) -> param.name == check.param }!!
                        if(param.second is ParsedTokenExpressionSimpleTyped && (param.second as ParsedTokenExpressionSimpleTyped).type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                            if(check.value == 0L) {
                                impl.appendText("false")
                            }else {
                                impl.appendText("true")
                            }
                        }else {
                            impl.appendText(check.value.toString())
                        }
                    }
                    impl.appendText(") return ")
                    mapValues(file.definitions.find { def -> def.name == cond.instruction }!!)
                    impl.appendText("\n")
                }
                impl.appendText("    }\n")
            }
            val writtenDefinitions = mutableListOf<String>()
            file.definitions.forEach {
                val named = it.name.uppercase()
                if(writtenDefinitions.contains(named)) {
                    return@forEach
                }
                writtenDefinitions.add(named)
                types.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.il.InstructionType $named = new dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\");\n")

                impl.appendText("fun ${named.snakeToLowerCamelCase()}(")
                for((param, type) in it.params) {
                    val typeName = when(param.scope) {
                        ParsedTokenValueAccessorScope.CONTEXT -> {
                            if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)) {
                                "dev.epicpix.minecraftfunctioncompiler.il.ScopeData"
                            }else {
                                "dev.epicpix.minecraftfunctioncompiler.il.LocationData"
                            }
                        }
                        ParsedTokenValueAccessorScope.INSTRUCTION -> {
                            if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)) {
                                "Int"
                            }else if(type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)) {
                                "Boolean"
                            }else if(type is ParsedTokenExpressionTypeComplex) {
                                type.typeName.run { if(startsWith("java.lang.")) substring(10) else this }
                            }else {
                                TODO()
                            }
                        }
                        ParsedTokenValueAccessorScope.LOCAL -> {
                            continue
                        }
                        ParsedTokenValueAccessorScope.STATIC -> {
                            continue
                        }
                    }

                    impl.appendText("${param.name.snakeToLowerCamelCase()}: $typeName, ")
                }
                impl.appendText("): $instructionType = $instructionType($enumClass.$named, mapOf(")
                val selfInfo = info[it.name]
                it.params.forEach param@{ (it, _) ->
                    if(it.scope == ParsedTokenValueAccessorScope.LOCAL || it.scope == ParsedTokenValueAccessorScope.STATIC) {
                        return@param
                    }
                    impl.appendText("\"${it.name}\" to ${it.name.snakeToLowerCamelCase()}")
                    if(it.scope == ParsedTokenValueAccessorScope.CONTEXT) {
                        val stats = selfInfo?.find { s -> s.accessor == it } ?: AccessorStatistics(it)
                        impl.appendText(".uses(/* source = */ ${stats.gets}, /* destination = */ ${stats.sets})")
                    }
                    impl.appendText(", ")
                }
                impl.appendText("))\n")
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
                        tree.backwardConnections.add(defs.find { treeDef -> treeDef.def.name.uppercase() == checkName } ?: throw NullPointerException("Definition with name '$checkName' not found"))
                    }
                }
            }
            val instructionEnumType = "dev.epicpix.minecraftfunctioncompiler.il.InstructionType"
            impl.appendText("\n    fun addStaticRequirements(instructions: List<$instructionType>): $instructionType {\n")
            impl.appendText("        val requirementInstructions = mutableSetOf<$instructionEnumType>()\n")
            impl.appendText("        instructions.forEach { addStaticRequirementsInternal(it, requirementInstructions) }\n")
            impl.appendText("        var baseInstruction = Instruction($enumClass.ROOT, mapOf(), instructions)\n")
            val defPriorities = defs.map { it.def to it.length() }.sortedByDescending { it.second }
            for((def, _) in defPriorities) {
                impl.appendText("        if(requirementInstructions.contains($enumClass.${def.name.uppercase()})) baseInstruction = $instructionType($enumClass.${def.name.uppercase()}, mapOf(), listOf(baseInstruction))\n")
            }
            impl.appendText("        return baseInstruction\n")
            impl.appendText("    }\n")
            impl.appendText("    private fun addStaticRequirementsInternal(instruction: $instructionType, mutableSet: MutableSet<$instructionEnumType>) {\n")
            impl.appendText("        instruction.children.forEach { addStaticRequirementsInternal(it, mutableSet) }\n")
            file.definitions.forEach {
                val staticParams = it.params.filter { (param, _) -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" && it.name.uppercase() != "static${param.name}".toUpperSnakeCase() }
                if(staticParams.isNotEmpty()) {
                    impl.appendText("        if(instruction.type == $enumClass.${it.name.uppercase()}) {\n")
                    for(param in staticParams) {
                        val paramName = "static${param.first.name}".toUpperSnakeCase()
                        val reqs = defs.find { it.def.name.uppercase() == paramName }!!.getAll()
                        for(req in reqs) {
                            impl.appendText("            mutableSet.add($enumClass.${req.def.name.uppercase()})\n")
                        }
                    }
                    impl.appendText("        }\n")
                }
            }
            impl.appendText("    }\n")

            impl.appendText("}\n")
            impl.close()
            types.appendText("}\n")
            types.close()
        }
    }

    @OptIn(KspExperimental::class)
    fun generateInstructionData(resolver: Resolver): Map<String, String> {
        val instructionMappings = mutableMapOf<String, String>()
        val classesList = resolver.getSymbolsWithAnnotation(InstructionSourceClass::class.java.name).toList()
        classesList.forEach {
            val annotation = it.getAnnotationsByType(InstructionSourceClass::class).first()
            val generatorClass = annotation.generatorClass
            val generatorResultPackage = generatorClass.substring(0, generatorClass.lastIndexOf('.'))
            val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.')+1)
            val enumClass = annotation.enumClass
            val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
            val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.')+1)

            val impl = codeGenerator.createNewFile(Dependencies(true, (it as KSClassDeclaration).containingFile!!), generatorResultPackage, generatorResultClass + "Impl", "kt")
            impl.appendText("package $generatorResultPackage\n")
            impl.appendText("data object $generatorResultClass {\n")

            val types = codeGenerator.createNewFile(Dependencies(true, it.containingFile!!), enumResultPackage, enumResultClass, "java")
            types.appendText("package $enumResultPackage;\n")
            types.appendText("public class $enumResultClass {\n")

            classesList.forEach {
                val defs = (it as KSClassDeclaration).getDeclaredFunctions()
                for(def in defs) {
                    if(def.origin != Origin.SYNTHETIC) {
                        val named = def.simpleName.asString().toUpperSnakeCase()
                        types.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.il.InstructionType $named = new dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\");\n")
                        instructionMappings[named] = "$enumClass.$named"
                        val params = mutableListOf<InstructionParamData>()
                        impl.appendText("fun ${def.simpleName.asString()}(")
                        for(param in def.parameters) {
                            var isSource = false
                            var isDestination = false
                            param.annotations.toList().forEach {
                                val qualified = it.annotationType.resolve().declaration.qualifiedName?.asString()
                                if(qualified == InstructionSource::class.java.name) isSource = true
                                if(qualified == InstructionDestination::class.java.name) isDestination = true
                            }

                            val resolvedType = param.type.resolve()
                            val typeName = resolvedType.declaration.qualifiedName!!.asString()
                            val typeArgs = resolvedType.arguments.joinToString { arg ->
                                arg.type?.resolve()?.declaration?.qualifiedName?.asString() ?: "*"
                            }

                            val fullTypeName = (if(typeArgs.isNotEmpty()) "$typeName<$typeArgs>" else typeName).run { if(resolvedType.isMarkedNullable) "$this?" else this }
                            impl.appendText("${param.name!!.asString()}: $fullTypeName, ")
                            params.add(InstructionParamData(
                                param.name!!.asString(),
                                resolvedType.isMarkedNullable,
                                resolvedType.declaration.qualifiedName!!.asString() == "kotlin.collections.List",
                                resolvedType.declaration.qualifiedName!!.asString() == "kotlin.collections.Map",
                                isSource,
                                isDestination))
                        }
                        val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"
                        impl.appendText("): $instructionType = $instructionType($enumClass.$named, mapOf(")
                        params.forEach {
                            impl.appendText("\"${it.name}\" to ${it.name}")
                            if(it.isSource) impl.appendText("${if(it.isNullable) "?" else ""}.source()")
                            if(it.isDestination) impl.appendText("${if(it.isNullable) "?" else ""}.destination()")
                            if(it.isList) impl.appendText("${if(it.isNullable) "?" else ""}.toList()")
                            if(it.isMap) impl.appendText("${if(it.isNullable) "?" else ""}.toMap()")
                            impl.appendText(", ")
                        }
                        impl.appendText("))\n")
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

fun String.toUpperSnakeCase() = split(Regex("(?=[A-Z])")).joinToString("_").uppercase()
fun String.snakeToLowerCamelCase() = Regex("_[a-zA-Z]").replace(lowercase()) { it.value.replace("_","").uppercase() }

data class InstructionParamData(val name: String, val isNullable: Boolean, val isList: Boolean, val isMap: Boolean, val isSource: Boolean, val isDestination: Boolean)

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}