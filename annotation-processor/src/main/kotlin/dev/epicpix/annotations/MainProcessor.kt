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
import org.objectweb.asm.Type
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
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

fun kotlinToJavaType(type: String, genericUsage: Boolean, generics: List<String> = emptyList()): String {
    val genericsString = if(generics.isEmpty()) "" else "<${generics.joinToString(", ") { "? extends $it" }}>"
    return when(type) {
        "kotlin.Boolean" -> if(!genericUsage) "boolean" else "Boolean"
        "kotlin.Double" -> if(!genericUsage) "double" else "Double"
        "kotlin.Float" -> if(!genericUsage) "float" else "Float"
        "kotlin.Int" -> if(!genericUsage) "int" else "Integer"
        "kotlin.Long" -> if(!genericUsage) "long" else "Long"
        "kotlin.String" -> "String"
        "kotlin.collections.List" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        "kotlin.collections.MutableList" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        "kotlin.collections.Map" -> "Map"
        "java.util.List" -> if(!genericUsage) "List" else "List${if(generics.isEmpty()) "<?>" else ""}"
        else -> if(type.startsWith("kotlin.")) throw UnsupportedOperationException("Unknown kotlin type: $type") else type
    } + genericsString
}

fun javaToMinecraftGenericType(type: String, generics: List<String> = emptyList()): String {
    if(type.startsWith("net.minecraft.")) {
        return when(type) {
            "net.minecraft.world.entity.Entity" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Entity"
            "net.minecraft.world.entity.LivingEntity" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.LivingEntity"
            "net.minecraft.server.level.ServerPlayer" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Player"
            "net.minecraft.world.scores.Objective" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Objective"
            "net.minecraft.world.scores.ScoreAccess" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Score"
            "net.minecraft.world.phys.Vec3" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Vec3"
            "net.minecraft.world.phys.Vec2" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Vec2"
            "net.minecraft.server.level.ServerLevel" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.World"
            "net.minecraft.world.entity.ai.attributes.AttributeInstance" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.AttributeInstance"
            "net.minecraft.nbt.CompoundTag" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.NbtCompound"
            "net.minecraft.nbt.Tag" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.NbtTag"
            "net.minecraft.world.scores.criteria.ObjectiveCriteria" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.ScoreboardCriterion"
            "net.minecraft.network.chat.Component" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.TextComponent"
            "net.minecraft.world.scores.Team" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.Team"
            "net.minecraft.world.level.GameType" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.GameType"
            "net.minecraft.nbt.ListTag" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.NbtList"
            "net.minecraft.commands.CommandSourceStack" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.CommandSourceStack"
            "net.minecraft.advancements.AdvancementProgress" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.AdvancementProgress"
            "net.minecraft.advancements.CriterionProgress" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.CriterionProgress"
            "net.minecraft.server.PlayerAdvancements" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.PlayerAdvancements"
            "net.minecraft.world.entity.Targeting" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.TargetingEntity"
            "net.minecraft.world.entity.TraceableEntity" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.TraceableEntity"
            "net.minecraft.world.Difficulty" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.DifficultyValue"
            "net.minecraft.world.entity.EntityType" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.EntityTypeValue"
            "net.minecraft.world.item.ItemStack" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.ItemStack"
            "net.minecraft.network.chat.Style" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.TextComponentStyle"
            "net.minecraft.network.chat.MutableComponent" -> "dev.epicpix.minecraftfunctioncompiler.data.locations.TextComponentMutable"
            else -> throw IllegalArgumentException("Unknown minecraft class: $type")
        }
    } else {
        return kotlinToJavaType(type, true, generics)
    }
}

var PACKAGE_MINECRAFT_VERSION: String? = null

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true
        dataDir = options["data_dir"]!!

        val jarFiles = mutableListOf<JarFile>()
        val jarFileMappings = mutableMapOf<String, Int>()
        if(options["jars"] != null) {
            val jars = options["jars"]!!.split(File.pathSeparator)
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
        PACKAGE_MINECRAFT_VERSION = options["minecraft_version"]?.replace(".", "_")?.run { "v$this" }
        val minecraftVersion = options["minecraft_version"]?.split(".")
        val definitions = options["definitions"]?.let { loadJson(it) }
        generateInstructionData(resolver)
        val targets = mutableListOf<String>()
        val file = parseCode(loadDataFile("data.instructions"))
        val emitterClass = (if(options["definitions"] == "common") file.fileInfo["emitter_noobf_file"]!! else file.fileInfo["emitter_obf_file"]!!).replace("_VERSION_", PACKAGE_MINECRAFT_VERSION
            ?: "common")
        val emitterPackage = emitterClass.substring(0, emitterClass.lastIndexOf('.'))
        val emitterTargetsClass = "$emitterPackage.EmitterTargets"
        if(definitions != null) {
            if(definitions["definition_file"] != null) {
                generateFunctionMapping(definitions, minecraftVersion?.getOrNull(1)?.toIntOrNull()
                    ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0, emitterTargetsClass, targets)
            }
        }
        if(options["definitions"] != null) {
            generateInstructionData(file, options["definitions"] == "common", minecraftVersion?.getOrNull(1)?.toIntOrNull()
                ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0, targets)
        }
        if(options["definitions"] == "common") {
            generateConstantsData()
            generateCommandsData()
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

    fun generateFunctionMapping(data: Map<String, JsonElement>, majorVer: Int, minorVer: Int, emitterTargetsClass: String, targets: MutableList<String>) {
        val resultClass = data["definition_file"]!!.asString.replace("_VERSION_", PACKAGE_MINECRAFT_VERSION ?: "common")
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
                        val minecraftVersion = k.split(".")
                        val cMajor = minecraftVersion[1].replace("+", "").replace("-", "").toInt()
                        val cMinor = minecraftVersion.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                        if(cMajor == majorVer && cMinor == minorVer) return v
                        else if(k.endsWith("+") && ((majorVer == cMajor && minorVer >= cMinor) || (majorVer > cMajor))) {
                            return v
                        } else if(k.endsWith("-") && ((majorVer == cMajor && minorVer <= cMinor) || (majorVer < cMajor))) {
                            return v
                        }
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
                val fullDescriptor = content.split("@")[0].replace(Regex.fromLiteral("_VERSION_")) {
                    PACKAGE_MINECRAFT_VERSION ?: throw IllegalArgumentException("Tried to use _VERSION_ in common env")
                }
                val className = fullDescriptor.split(":")[0]
                val methodDescriptor = Type.getMethodType("(" + fullDescriptor.split("(")[1])
                val methodName = fullDescriptor.split(":")[1].split("(")[0]
                val classInfo = loadClassInfo(className.replace('/', '.').replace('$', '.'))
                val methodInfo = classInfo.methods.find { f -> f.name == methodName && f.descriptor == methodDescriptor.descriptor }?.checkPublic() ?: throw IllegalArgumentException("Failed to find method $className:$methodName$methodDescriptor")

                val callDetails = content.split("@").getOrElse(1) { "" }
                val noReorder = callDetails.contains("N")
                val target = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FunctionTarget(\"$className\", \"$methodName\", \"${methodDescriptor.descriptor}\", ${methodInfo.isStatic}, ${classInfo.isInterface}, ${methodName == "<init>"}, ${noReorder})"
                val args = mutableListOf<String>()
                if(!methodInfo.isStatic) args.add("self")
                for(arg in methodDescriptor.argumentTypes.indices) {
                    args.add("r$arg")
                }

                if(methodDescriptor.returnType == Type.VOID_TYPE) {
                    mapExtensions.appendText("    public static void call$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter${args.joinToString("") { ", dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue $it" }}) { codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}]${args.joinToString("") { ", $it" }}); }\n")
                } else {
                    mapExtensions.appendText("    public static dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister call$k(dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter codeWriter${args.joinToString("") { ", dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue $it" }}) { return codeWriter.writeTarget(TARGETS[${getTarget(target, targets)}]${args.joinToString("") { ", $it" }}); }\n")
                }
            }
        }
        data["definitions_fields"]?.asJsonObject?.asMap()?.forEach { (k, v) ->
            val content = getForCurrentVersion(v)
            if(content != null) {
                val fullDescriptor = content.split("@")[0].replace(Regex.fromLiteral("_VERSION_")) {
                    PACKAGE_MINECRAFT_VERSION
                        ?: throw IllegalArgumentException("Tried to use _VERSION_ in a common env")
                }
                val className = fullDescriptor.split(":")[0]
                val fieldName = fullDescriptor.split(":")[1]
                val fieldDescriptor = fullDescriptor.split(":")[2]
                val fieldInfo = loadClassInfo(className.replace('/', '.').replace('$', '.')).fields.find { f -> f.name == fieldName && f.descriptor == fieldDescriptor }?.checkPublic() ?: throw IllegalArgumentException("Failed to find field $className:$fieldName:$fieldDescriptor")

                val callDetails = content.split("@").getOrNull(1)
                if(callDetails != null) {
                    val isStatic = fieldInfo.isStatic
                    if(callDetails.contains("R")) {
                        val target = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FieldTarget(\"$className\", \"$fieldName\", \"$fieldDescriptor\", ${isStatic}, false)"
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
                        val target = "new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FieldTarget(\"$className\", \"$fieldName\", \"$fieldDescriptor\", ${isStatic}, true)"
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
    }

    fun generateInstructionData(file: ParsedFile, common: Boolean, majorVer: Int, minorVer: Int, targets: MutableList<String>) {
        val resultClass = (if(common) file.fileInfo["emitter_noobf_file"]!! else file.fileInfo["emitter_obf_file"]!!).replace("_VERSION_", PACKAGE_MINECRAFT_VERSION
            ?: "common")
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.') + 1)
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("import static ${resultPackage}.EmitterTargets.TARGETS;\n")
        map.appendText("public class $resultName {\n")
        map.appendText("    public static void registerInstructionEmitters() {\n")
        map.appendText("        dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions.registerEmitterInstruction((context) -> {\n")
        map.appendText("            var cw = context.codeWriter();\n")
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
                    val codeGenResult = generateCode(it, file, output, majorVer, minorVer, targets)
                    info[it.name] = codeGenResult.first
                    map.appendText(output.toByteArray().decodeToString().trimEnd().prependIndent("                "))
                    map.appendText("\n")
                    if(codeGenResult.second) {
                        map.appendText("                return true;\n")
                    }
                    map.appendText("            }\n")
                }
            }
        }
        map.appendText("            return false;\n")
        map.appendText("        });\n")
        map.appendText("    }\n")
        map.appendText("\n")
        map.appendText("}\n")

        val mapTargets = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, "EmitterTargets", "java")
        mapTargets.appendText("package $resultPackage;\n")
        mapTargets.appendText("public class EmitterTargets {\n")
        mapTargets.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget[] TARGETS = {\n")
        for(targetIndex in targets.indices) {
            val target = targets[targetIndex]
            if(targetIndex != targets.size - 1) {
                mapTargets.appendText("        $target,\n")
            } else {
                mapTargets.appendText("        $target\n")
            }
        }
        mapTargets.appendText("    };\n")
        mapTargets.appendText("}\n")

        if(common) {
            val generatorClass = file.fileInfo["generator_file"]!!
            val generatorResultPackage = generatorClass.substring(0, generatorClass.lastIndexOf('.'))
            val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.') + 1)
            val enumClass = file.fileInfo["enum_file"]!!
            val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
            val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.') + 1)

            val impl = codeGenerator.createNewFile(Dependencies.ALL_FILES, generatorResultPackage, generatorResultClass, "java")
            impl.appendText("package $generatorResultPackage;\n")
            impl.appendText("import java.util.*;\n")
            impl.appendText("public final class $generatorResultClass {\n")

            val types = codeGenerator.createNewFile(Dependencies.ALL_FILES, enumResultPackage, enumResultClass, "java")
            types.appendText("package $enumResultPackage;\n")
            types.appendText("public class $enumResultClass {\n")
            val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"

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
                            generics.append("${type.generic.name} extends ${javaToMinecraftGenericType(type.lowerBound.typeName, type.lowerBound.generics.map { javaToMinecraftGenericType(it.typeName) })}")
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
                                                javaToMinecraftGenericType(type.typeName, type.generics.map { javaToMinecraftGenericType(it.typeName) })
                                            } else {
                                                "? extends ${javaToMinecraftGenericType(type.typeName, type.generics.map { javaToMinecraftGenericType(it.typeName) })}"
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
                                is ParsedTokenExpressionTypeComplex -> kotlinToJavaType(type.typeName, false, type.generics.map { javaToMinecraftGenericType(it.typeName) })
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
            val generatorResultClass = generatorClass.substring(generatorClass.lastIndexOf('.') + 1)
            val enumClass = annotation.enumClass
            val enumResultPackage = enumClass.substring(0, enumClass.lastIndexOf('.'))
            val enumResultClass = enumClass.substring(enumClass.lastIndexOf('.') + 1)

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

fun String.toUpperSnakeCase() = split(Regex("(?=[A-Z])")).joinToString("_").uppercase()
fun String.snakeToLowerCamelCase() = Regex("_[a-zA-Z]").replace(lowercase()) { it.value.replace("_", "").uppercase() }

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}