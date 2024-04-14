package dev.epicpix.annotations

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Origin
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyUtils
import org.objectweb.asm.Type
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Paths

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

data class MappingData(
    val mappedClasses: MutableMap<String, String> = mutableMapOf(),
    val mappedMethods: MutableMap<IMappingProvider.Member, String> = mutableMapOf(),
    val mappedFields: MutableMap<IMappingProvider.Member, String> = mutableMapOf(),
)

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

        val minecraftVersion = options["minecraft_version"]?.split(".")
        val definitions = options["definitions"]?.let { loadToml(it) }
        generateInstructionData(resolver)
        if(definitions != null) {
            if(definitions["definition_file"] != null) {
                generateFunctionMapping(mappingData, definitions, minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0)
            }
        }
        if(options["definitions"] != null) {
            generateInstructionData(mappingData, options["definitions"] == "common", minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0)
        }
        generateConstantsData(resolver)

        return listOf()
    }

    @OptIn(KspExperimental::class)
    fun generateConstantsData(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(ConstantsFile::class.java.name).forEach {
            val resultClass = it.getAnnotationsByType(ConstantsFile::class).first().value
            val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
            val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
            val map = codeGenerator.createNewFile(Dependencies(true, it as KSFile), resultPackage, resultName, "kt")
            map.appendText("package $resultPackage\n")
            map.appendText("const val isDevelopmentEnvironment: Boolean = ${options["isJarTask"] != "true"}\n")
            map.appendText("const val versionCommit: String = \"${options["commit"] ?: "development"}\"\n")
            map.appendText("const val versionName: String = \"${options["version"] ?: error("Version option not specified")}\"\n")
            map.close()
        }
    }

    fun generateFunctionMapping(mappingData: MappingData?, data: Map<String, Any>, majorVer: Int, minorVer: Int) {
        val resultClass = data["definition_file"] as String
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

    fun generateInstructionData(mappingData: MappingData?, common: Boolean, majorVer: Int, minorVer: Int) {
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

        val resultClass = if(common) file.fileInfo["emitter_noobf_file"]!! else file.fileInfo["emitter_obf_file"]!!
        val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
        val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
        map.appendText("package $resultPackage;\n")
        map.appendText("public class $resultName {\n")
        map.appendText("    public static void registerInstructionEmitters() {\n")
        file.definitions.forEach {
            if(it.usesObf != common) {
                map.appendText("        dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructionsKt.registerEmitterInstruction(${file.fileInfo["enum_file"]}.${it.name.uppercase()}, (context) -> {\n")
                val output = ByteArrayOutputStream()
                generateCode(mappingData, it, file, output)
                map.appendText(output.toByteArray().decodeToString().trimEnd().prependIndent("            "))
                map.appendText("\n")
                map.appendText("        });\n")
            }
        }
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

            file.definitions.forEach {
                val named = it.name.uppercase()
                types.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.il.InstructionType $named = new dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\");\n")

                impl.appendText("fun ${named.snakeToLowerCamelCase()}(")
                for(param in it.params) {
                    val typeName = when(param.scope) {
                        ParsedTokenValueAccessorScope.CONTEXT -> {
                            if(param.type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.SCOPE)) {
                                "dev.epicpix.minecraftfunctioncompiler.il.ScopeData"
                            }else {
                                "dev.epicpix.minecraftfunctioncompiler.il.LocationData"
                            }
                        }
                        ParsedTokenValueAccessorScope.INSTRUCTION -> {
                            if(param.type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.INTEGER)) {
                                "Int"
                            }else if(param.type == ParsedTokenExpressionSimpleTyped(ParsedTokenExpressionSimpleType.BOOLEAN)) {
                                "Boolean"
                            }else if(param.type is ParsedTokenExpressionTypeComplex) {
                                param.type.typeName.run { if(startsWith("java.lang.")) substring(10) else this }
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
                it.params.forEach param@{
                    if(it.scope == ParsedTokenValueAccessorScope.LOCAL || it.scope == ParsedTokenValueAccessorScope.STATIC) {
                        return@param
                    }
                    impl.appendText("\"${it.name}\" to ${it.name.snakeToLowerCamelCase()}")
                    if(it.scope == ParsedTokenValueAccessorScope.CONTEXT) {
                        impl.appendText(".uses(source = ${it.gets}, destination = ${it.sets})")
                    }
                    impl.appendText(", ")
                }
                impl.appendText("))\n")
            }

            val instructionEnumType = "dev.epicpix.minecraftfunctioncompiler.il.InstructionType"
            impl.appendText("\n    fun addStaticRequirements(instructions: List<$instructionType>): $instructionType {\n")
            impl.appendText("        val requirementInstructions = mutableSetOf<$instructionEnumType>()\n")
            impl.appendText("        instructions.forEach { addStaticRequirementsInternal(it, requirementInstructions) }\n")
            impl.appendText("        var baseInstruction = Instruction($enumClass.ROOT, mapOf(), instructions.toMutableList())\n")
            impl.appendText("        val addedRequirements = mutableSetOf<$instructionEnumType>()\n")
            impl.appendText("        while(requirementInstructions.isNotEmpty()) {\n")
            impl.appendText("            baseInstruction = includeRequirementInstruction(baseInstruction, addedRequirements, requirementInstructions.toList()[0])\n")
            impl.appendText("            requirementInstructions.removeAll(addedRequirements)\n")
            impl.appendText("        }\n")
            impl.appendText("        return baseInstruction\n")
            impl.appendText("    }\n")
            impl.appendText("    private fun includeRequirementInstruction(instruction: $instructionType, addedRequirements: MutableSet<$instructionEnumType>, currentRequirement: $instructionEnumType): $instructionType {\n")
            impl.appendText("        var result = instruction\n")
            file.definitions.filter { def -> def.name.startsWith("static_") }.forEach {
                val staticParams = it.params.filter { param -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" }
                if(staticParams.isNotEmpty()) {
                    impl.appendText("        if(currentRequirement == $enumClass.${it.name.uppercase()}) {\n")
                    impl.appendText("            addedRequirements.add($enumClass.${it.name.uppercase()})\n")
                    impl.appendText("            result = $instructionType($enumClass.${it.name.uppercase()}, mapOf(), mutableListOf(result))\n")
                    for(param in staticParams.filter { param -> it.name.uppercase() != "static${param.name}".toUpperSnakeCase() }) {
                        impl.appendText("            if(!addedRequirements.contains($enumClass.${"static${param.name}".toUpperSnakeCase()})) {\n")
                        impl.appendText("                result = includeRequirementInstruction(result, addedRequirements, $enumClass.${"static${param.name}".toUpperSnakeCase()})\n")
                        impl.appendText("            }\n")
                    }
                    impl.appendText("        }\n")
                }
            }
            impl.appendText("        return result\n")
            impl.appendText("    }\n")
            impl.appendText("    private fun addStaticRequirementsInternal(instruction: $instructionType, mutableSet: MutableSet<$instructionEnumType>) {\n")
            impl.appendText("        instruction.children.forEach { addStaticRequirementsInternal(it, mutableSet) }\n")
            file.definitions.forEach {
                val staticParams = it.params.filter { param -> param.scope == ParsedTokenValueAccessorScope.STATIC && param.name != "CommandSourceStack" && it.name.uppercase() != "static${param.name}".toUpperSnakeCase() }
                if(staticParams.isNotEmpty()) {
                    impl.appendText("        if(instruction.type == $enumClass.${it.name.uppercase()}) {\n")
                    for(param in staticParams) {
                        impl.appendText("            mutableSet.add($enumClass.${"static${param.name}".toUpperSnakeCase()})\n")
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

    fun remapType(type: Type, mappingData: MappingData?): Type {
        if(mappingData == null) return type
        if(type.sort == Type.METHOD) {
            val params = type.argumentTypes.map { remapType(it, mappingData) }
            return Type.getMethodType(type.returnType.run { remapType(this, mappingData) }, *params.toTypedArray())
        }
        if(type.sort != Type.OBJECT) return type
        return Type.getType("L" + (mappingData.getClassName(type.descriptor!!.run { substring(1, length - 1) })) + ";")
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