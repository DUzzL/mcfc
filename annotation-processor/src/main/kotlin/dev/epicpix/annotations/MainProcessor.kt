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
import dev.epicpix.annotations.enums.EnumList
import dev.epicpix.annotations.enums.parseEnums
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

private lateinit var dataDir: String

fun loadToml(file: String): Map<String, Any> {
    return tomlMapper {  }.decode(loadDataFile("$file.toml"))
}

fun loadDataFile(file: String): String {
    return Files.readString(File(dataDir, file).toPath())
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

fun kotlinToJavaType(type: String, genericUsage: Boolean): String {
    return when(type) {
        "kotlin.Boolean" -> if(!genericUsage) "boolean" else "Boolean"
        "kotlin.Double" -> if(!genericUsage) "double" else "Double"
        "kotlin.Float" -> if(!genericUsage) "float" else "Float"
        "kotlin.Int" -> if(!genericUsage) "int" else "Integer"
        "kotlin.String" -> "String"
        "kotlin.collections.List" -> if(!genericUsage) "List" else "List<?>"
        "kotlin.collections.MutableList" -> if(!genericUsage) "List" else "List<?>"
        "kotlin.collections.Map" -> "Map"
        "java.util.List" -> if(!genericUsage) "List" else "List<?>"
        else -> if(type.startsWith("kotlin.")) throw UnsupportedOperationException("Unknown kotlin type: $type") else type
    }
}

fun javaToMinecraftGenericType(type: String): String {
    if(type.startsWith("net.minecraft.")) {
        return when(type) {
            "net.minecraft.world.entity.Entity" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Entity"
            "net.minecraft.world.entity.LivingEntity" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.LivingEntity"
            "net.minecraft.server.level.ServerPlayer" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Player"
            "net.minecraft.world.scores.Objective" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Objective"
            "net.minecraft.world.scores.Score" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Score"
            "net.minecraft.world.phys.Vec3" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Vec3"
            "net.minecraft.world.phys.Vec2" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Vec2"
            "net.minecraft.server.level.ServerLevel" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.World"
            "net.minecraft.world.entity.ai.attributes.AttributeInstance" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.AttributeInstance"
            "net.minecraft.nbt.CompoundTag" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.NbtCompound"
            "net.minecraft.nbt.Tag" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.NbtTag"
            else -> throw IllegalArgumentException("Unknown minecraft class: $type")
        }
    }else {
        return kotlinToJavaType(type, true)
    }
}

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true
        dataDir = options["data_dir"]!!

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
            val enums = parseEnums(loadDataFile("data.enums"))
            generateInstructionData(mappingData, options["definitions"] == "common", minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0, packageMinecraftVersion, enums)
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
        val data = parseCommandsData(loadDataFile("data.commands"))
        run {
            val resultPackage = "dev.epicpix.minecraftfunctioncompiler.commands"
            val resultName = "CommandParser"
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
            map.appendText("package $resultPackage;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;\n")
            map.appendText("import dev.epicpix.minecraftfunctioncompiler.data.*;\n")
            map.appendText("import java.util.*;\n")
            map.appendText("public final class $resultName {\n")
            map.appendText("    private $resultName() {}\n")
            map.appendText(generateCommandsDataReader(data).substringBeforeLast('\n').prependIndent("    ") + "\n")
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
        val mapExtensions = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        mapExtensions.appendText("package $resultPackage;\n")
        mapExtensions.appendText("public final class $resultName {\n")
        mapExtensions.appendText("    private $resultName() {}\n")
        fun findVersionSpecific(v: Any?): String? {
            if(v is Map<*, *>) {
                v.forEach { (k, v) ->
                    k as String
                    v as String
                    val minecraftVersion = k.split(".")
                    val cMajor = minecraftVersion[1].replace("+", "").replace("-", "").toInt()
                    val cMinor = minecraftVersion.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
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
                    mapExtensions.appendText("    public static void call$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { codeWriter.methodVisitor().visitMethodInsn($opcode, \"$className\", \"$methodName\", \"$methodDescriptor\", $isInterface); }\n")
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
                        if(callDetails.contains("R")) mapExtensions.appendText("    public static void load$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { codeWriter.methodVisitor().visitFieldInsn(178, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                        if(callDetails.contains("W")) mapExtensions.appendText("    public static void store$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { codeWriter.methodVisitor().visitFieldInsn(179, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                    }else {
                        if(callDetails.contains("R")) mapExtensions.appendText("    public static void load$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { codeWriter.methodVisitor().visitFieldInsn(180, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                        if(callDetails.contains("W")) mapExtensions.appendText("    public static void store$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter) { codeWriter.methodVisitor().visitFieldInsn(181, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                    }
                }
            }
        }
        mapExtensions.appendText("}\n")
    }

    fun generateInstructionData(mappingData: MappingData?, common: Boolean, majorVer: Int, minorVer: Int, packageMinecraftVersion: String?, enums: EnumList) {
        val file = parseCode(loadDataFile("data.instructions"))

        val resultClass = (if(common) file.fileInfo["emitter_noobf_file"]!! else file.fileInfo["emitter_obf_file"]!!).replace("\$VERSION", packageMinecraftVersion ?: "common")
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("public class $resultName {\n")
        map.appendText("    public static void registerInstructionEmitters() {\n")
        map.appendText("        dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions.registerEmitterInstruction((context) -> {\n")
        map.appendText("            var cw = context.codeWriter();\n")
        map.appendText("            var mv = cw.methodVisitor();\n")
        map.appendText("            var type = context.instruction().type();\n")
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
                    info[it.name] = generateCode(mappingData, it, file, output, enums, majorVer, minorVer)
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

            val impl = codeGenerator.createNewFile(Dependencies.ALL_FILES, generatorResultPackage, generatorResultClass, "java")
            impl.appendText("package $generatorResultPackage;\n")
            impl.appendText("import java.util.*;\n")
            impl.appendText("public final class ${generatorResultClass} {\n")

            val types = codeGenerator.createNewFile(Dependencies.ALL_FILES, enumResultPackage, enumResultClass, "java")
            types.appendText("package $enumResultPackage;\n")
            types.appendText("public class $enumResultClass {\n")
            val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"

            file.collapsableDefinitions.forEach {
                val named = it.name.uppercase()
                impl.appendText("public static $instructionType ${named.snakeToLowerCamelCase()}(")
                for(i in it.params.indices) {
                    val (param, type) = it.params[i];
                    val typeName = when(param.scope) {
                        ParsedTokenValueAccessorScope.CONTEXT -> {
                            if(type == ParsedTokenExpressionSimpleType.SCOPE) {
                                "dev.epicpix.minecraftfunctioncompiler.il.ScopeData"
                            }else {
                                "dev.epicpix.minecraftfunctioncompiler.il.LocationData<${
                                    if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                                        "Integer"
                                    }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                                        "Boolean"
                                    }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                                        "Double"
                                    }else if(type is ParsedTokenExpressionTypeComplex) {
                                        javaToMinecraftGenericType(type.typeName)
                                    }else {
                                        TODO("Unknown type: $type")
                                    }
                                }>"
                            }
                        }
                        ParsedTokenValueAccessorScope.INSTRUCTION -> {
                            if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                                "int"
                            }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                                "boolean"
                            }else if(type is ParsedTokenExpressionTypeComplex) {
                                kotlinToJavaType(type.typeName, false)
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
                    impl.appendText("$typeName ${param.name.snakeToLowerCamelCase()}")
                    if(i < it.params.size - 1) {
                        impl.appendText(", ")
                    }
                }
                impl.appendText(") {\n")
                fun mapValues(to: ParsedTokenInstructionDefinition) {
                    impl.appendText(to.name.uppercase().snakeToLowerCamelCase())
                    impl.appendText("(")
                    for(i in to.params.indices) {
                        val (value, _) = to.params[i]
                        if(value.scope != ParsedTokenValueAccessorScope.STATIC) {
                            impl.appendText(value.name)
                            if(i < to.params.size - 1) {
                                impl.appendText(", ")
                            }
                        }
                    }
                    impl.appendText(")")
                }
                for(cond in it.conditions) {
                    if(cond.checks.isEmpty()) {
                        impl.appendText("        return ")
                        mapValues(file.definitions.find { def -> def.name == cond.instruction }!!)
                        impl.appendText(";\n")
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
                        if(param.second == ParsedTokenExpressionSimpleType.BOOLEAN) {
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
                    impl.appendText(";\n")
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
                if(named.startsWith("STATIC_")) {
                    return@forEach
                }
                impl.appendText("public static $instructionType ${named.snakeToLowerCamelCase()}(")
                for(i in it.params.indices) {
                    val (param, type) = it.params[i];
                    val typeName = when(param.scope) {
                        ParsedTokenValueAccessorScope.CONTEXT -> {
                            if(type == ParsedTokenExpressionSimpleType.SCOPE) {
                                "dev.epicpix.minecraftfunctioncompiler.il.ScopeData"
                            }else {
                                "dev.epicpix.minecraftfunctioncompiler.il.LocationData<${
                                    if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                                        "Integer"
                                    }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                                        "Boolean"
                                    }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                                        "Double"
                                    }else if(type is ParsedTokenExpressionTypeComplex) {
                                        javaToMinecraftGenericType(type.typeName)
                                    }else {
                                        TODO("Unknown type: $type")
                                    }
                                }>"
                            }
                        }
                        ParsedTokenValueAccessorScope.INSTRUCTION -> {
                            if(type == ParsedTokenExpressionSimpleType.INTEGER) {
                                "int"
                            }else if(type == ParsedTokenExpressionSimpleType.BOOLEAN) {
                                "boolean"
                            }else if(type == ParsedTokenExpressionSimpleType.DOUBLE) {
                                "double"
                            }else if(type is ParsedTokenExpressionTypeComplex) {
                                kotlinToJavaType(type.typeName, false)
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

                    impl.appendText("$typeName ${param.name.snakeToLowerCamelCase()}")
                    if(i < it.params.size - 1) {
                        impl.appendText(", ")
                    }
                }
                impl.appendText(") {")
                impl.appendText("HashMap<String, Object> hashMap = new HashMap<>();")
                it.params.forEach param@{ (it, _) ->
                    if(it.scope == ParsedTokenValueAccessorScope.LOCAL || it.scope == ParsedTokenValueAccessorScope.STATIC) {
                        return@param
                    }
                    impl.appendText("hashMap.put(\"${it.name}\", ${it.name.snakeToLowerCamelCase()});")
                }
                impl.appendText("return new $instructionType($enumClass.$named, hashMap); }\n")
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
            impl.appendText("\n    public static $instructionType addStaticRequirements($instructionType instruction) {\n")
            impl.appendText("        HashSet<$instructionEnumType> requirementInstructions = new HashSet<>();\n")
            impl.appendText("        addStaticRequirementsInternal(instruction, requirementInstructions);\n")
            impl.appendText("        Instruction baseInstruction = instruction;\n")
            val defPriorities = defs.map { it.def to it.length() }.sortedByDescending { it.second }
            for((def, _) in defPriorities) {
                impl.appendText("        if(requirementInstructions.contains($enumClass.${def.name.uppercase()})) baseInstruction = new $instructionType($enumClass.${def.name.uppercase()}, new HashMap<>(), List.of(baseInstruction));\n")
            }
            impl.appendText("        return baseInstruction;\n")
            impl.appendText("    }\n")
            impl.appendText("    private static void addStaticRequirementsInternal($instructionType instruction, HashSet<$instructionEnumType> mutableSet) {\n")
            impl.appendText("        for(var it : instruction.children()) { addStaticRequirementsInternal(it, mutableSet); }\n")
            file.definitions.forEach {
                val staticParams = it.params.filter { (param, _) -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" && it.name.uppercase() != "static${param.name}".toUpperSnakeCase() }
                if(staticParams.isNotEmpty()) {
                    impl.appendText("        if(instruction.type() == $enumClass.${it.name.uppercase()}) {\n")
                    for(param in staticParams) {
                        val paramName = "static${param.first.name}".toUpperSnakeCase()
                        val reqs = defs.find { it.def.name.uppercase() == paramName }!!.getAll()
                        for(req in reqs) {
                            impl.appendText("            mutableSet.add($enumClass.${req.def.name.uppercase()});\n")
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

            val impl = codeGenerator.createNewFile(Dependencies(true, (it as KSClassDeclaration).containingFile!!), generatorResultPackage, generatorResultClass, "java")
            impl.appendText("package $generatorResultPackage;\n")
            impl.appendText("import java.util.*;\n")
            impl.appendText("public final class $generatorResultClass {\n")

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
                        val params = mutableListOf<String>()
                        val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"
                        impl.appendText("public static $instructionType ${def.simpleName.asString()}(")
                        for(i in def.parameters.indices) {
                            val param = def.parameters[i]
                            val resolvedType = param.type.resolve()
                            val typeName = kotlinToJavaType(resolvedType.declaration.qualifiedName!!.asString(), false)
                            val typeArgs = resolvedType.arguments.joinToString { arg ->
                                arg.type?.resolve()?.declaration?.qualifiedName?.asString()?.run {
                                    kotlinToJavaType(this, true)
                                } ?: "Object"
                            }

                            val fullTypeName = if(typeArgs.isNotEmpty()) "$typeName<$typeArgs>" else typeName
                            impl.appendText("$fullTypeName ${param.name!!.asString()}")
                            if(i < def.parameters.size - 1) {
                                impl.appendText(", ")
                            }
                            params.add(param.name!!.asString())
                        }
                        impl.appendText(") {")
                        impl.appendText("HashMap<String, Object> hashMap = new HashMap<>();")
                        params.forEach {
                            impl.appendText("hashMap.put(\"$it\", $it);")
                        }
                        impl.appendText("return new $instructionType($enumClass.$named, hashMap); }\n")
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

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}