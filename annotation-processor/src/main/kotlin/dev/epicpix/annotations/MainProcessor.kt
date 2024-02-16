package dev.epicpix.annotations

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Origin
import com.google.gson.Gson
import com.google.gson.JsonElement
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyUtils
import org.objectweb.asm.Type
import java.io.OutputStream
import java.nio.file.Paths
import java.util.*

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

fun loadJson(file: String): JsonElement {
    val str = MainProcessor::class.java.getResourceAsStream("/data/$file.json")?.readAllBytes()?.decodeToString()
        ?: throw NullPointerException("Failed to read file $file.json")
    return Gson().fromJson(str, JsonElement::class.java)
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
        generateFunctionMapping(resolver, mappingData, options["definitions"], minecraftVersion?.getOrNull(1)?.toIntOrNull() ?: 1, minecraftVersion?.getOrNull(2)?.toIntOrNull() ?: 0)
        generateInstructionData(resolver)
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

    @OptIn(KspExperimental::class)
    fun generateFunctionMapping(resolver: Resolver, mappingData: MappingData?, definitionFile: String?, majorVer: Int, minorVer: Int) {
        resolver.getSymbolsWithAnnotation(MapSourceClass::class.java.name).forEach {
            val resultClass = it.getAnnotationsByType(MapSourceClass::class).first().value
            val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
            val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
            val map = codeGenerator.createNewFile(Dependencies(true, (it as KSClassDeclaration).containingFile!!), resultPackage, resultName, "kt")
            map.appendText("package $resultPackage\n")
            map.appendText("object $resultName {\n")
            map.appendText("    fun init() {\n")
            generateFunctionMappingPer(map, mappingData, it)
            map.appendText("    }\n")
            map.appendText("}\n")
            map.close()
        }

        if(definitionFile != null) {
            val data = loadJson(definitionFile).asJsonObject
            val resultClass = data["definition_file"].asString
            val resultPackage = resultClass.substring(0, resultClass.lastIndexOf('.'))
            val resultName = resultClass.substring(resultClass.lastIndexOf('.')+1)
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, resultName, "java")
            map.appendText("package $resultPackage;\n")
            map.appendText("public class $resultName {\n")
            val mapExtensions = codeGenerator.createNewFile(Dependencies.ALL_FILES, resultPackage, "${resultName}Extensions", "kt")
            mapExtensions.appendText("package $resultPackage\n")
            fun findVersionSpecific(v: JsonElement): String? {
                if(v.isJsonObject) {
                    v.asJsonObject.entrySet().forEach { (k, v) ->
                        val minecraftVersion = k.split(".")
                        val cMajor = minecraftVersion[1].toInt()
                        val cMinor = minecraftVersion[2].filter { it.isDigit() }.toIntOrNull() ?: 0
                        if(cMajor == majorVer && cMinor == minorVer) return v.asString
                        else if( k.endsWith("+") && ((majorVer == cMajor && minorVer >= cMinor) || (majorVer > cMajor))) {
                            return v.asString
                        }else if(k.endsWith("-") && ((majorVer == cMajor && minorVer <= cMinor) || (majorVer < cMajor))) {
                            return v.asString
                        }
                    }
                    return null
                }else {
                    return v.asString
                }
            }
            data["definitions_functions"]?.asJsonObject?.entrySet()?.forEach { (k, v) ->
                val content = findVersionSpecific(v)
                if(content != null) {
                    val fullDescriptor = content.split("@")[0]
                    val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                    val methodDescriptor = remapType(Type.getMethodType("(" + fullDescriptor.split("(")[1]), mappingData).descriptor
                    val methodName = mappingData.getMethodName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1].split("(")[0], "(" + fullDescriptor.split("(")[1])
                    map.appendText("    public static final dev.epicpix.minecraftfunctioncompiler.emitter.MappedFunctionData $k = new dev.epicpix.minecraftfunctioncompiler.emitter.MappedFunctionData(\"$className\", \"$methodName\", \"$methodDescriptor\");\n")
                    val callDetails = content.split("@").getOrNull(1)
                    if(callDetails != null) {
                        val isStatic = !callDetails.contains("M")
                        val isInterface = callDetails.contains("I")
                        val opcode = if(isStatic) 184 else if(isInterface) 185 else 182
                        mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.call$k(params: () -> Unit) { params(); methodVisitor.visitMethodInsn($opcode, \"$className\", \"$methodName\", \"$methodDescriptor\", $isInterface); }\n")
                    }
                }
            }
            data["definitions_fields"]?.asJsonObject?.entrySet()?.forEach { (k, v) ->
                val content = findVersionSpecific(v)
                if(content != null) {
                    val fullDescriptor = content.split("@")[0]
                    val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                    val fieldDescriptor = remapType(Type.getType(fullDescriptor.split(":")[2]), mappingData).descriptor
                    val fieldName = mappingData.getFieldName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1], fullDescriptor.split(":")[2])
                    val callDetails = v.asString.split("@").getOrNull(1)
                    if(callDetails != null) {
                        val isStatic = !callDetails.contains("M")
                        if(isStatic) {
                            mapExtensions.appendText("@Suppress(\"NOTHING_TO_INLINE\") inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.load$k() { methodVisitor.visitFieldInsn(178, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                            mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.store$k(value: () -> Unit) { value(); methodVisitor.visitFieldInsn(179, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                        }else {
                            mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.load$k(params: () -> Unit) { params(); methodVisitor.visitFieldInsn(180, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                            mapExtensions.appendText("inline fun dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter.store$k(params: () -> Unit) { params(); methodVisitor.visitFieldInsn(181, \"$className\", \"$fieldName\", \"$fieldDescriptor\"); }\n")
                        }
                    }
                }
            }
            map.appendText("}\n")
            map.close()
        }
    }

    private fun generateFunctionMappingPer(map: OutputStream, mappingData: MappingData?, clzDefinition: KSClassDeclaration) {
        val defs = clzDefinition.getDeclaredProperties()
        for(def in defs) {
            if(def.origin != Origin.SYNTHETIC) {
                def.annotations.toList().forEach {
                    val qualified = it.annotationType.resolve().declaration.qualifiedName?.asString()
                    if(qualified == MapFieldUse::class.java.name) {
                        val fullDescriptor = it.arguments.find { it.name!!.asString() == "descriptor" }!!.value as String
                        val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                        val fieldDescriptor = remapType(Type.getType(fullDescriptor.split(":")[2]), mappingData).descriptor
                        val fieldName = mappingData.getFieldName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1], fullDescriptor.split(":")[2])

                        map.appendText("        ${def.qualifiedName!!.asString()} = dev.epicpix.minecraftfunctioncompiler.emitter.MappedFieldData(\"$className\", \"$fieldName\", \"$fieldDescriptor\")\n".replace("\$", "\\\$"))
                    }
                    if(qualified == MapFunctionUse::class.java.name) {
                        val fullDescriptor = it.arguments.find { it.name!!.asString() == "descriptor" }!!.value as String
                        val className = mappingData.getClassName(fullDescriptor.split(":")[0])
                        val methodDescriptor = remapType(Type.getMethodType("(" + fullDescriptor.split("(")[1]), mappingData).descriptor
                        val methodName = mappingData.getMethodName(fullDescriptor.split(":")[0], fullDescriptor.split(":")[1].split("(")[0], "(" + fullDescriptor.split("(")[1])

                        map.appendText("        ${def.qualifiedName!!.asString()} = dev.epicpix.minecraftfunctioncompiler.emitter.MappedFunctionData(\"$className\", \"$methodName\", \"$methodDescriptor\")\n".replace("\$", "\\\$"))
                    }
                }
            }
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
    fun generateInstructionData(resolver: Resolver) {
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

            val types = codeGenerator.createNewFile(Dependencies(true, it.containingFile!!), enumResultPackage, enumResultClass, "kt")
            types.appendText("package $enumResultPackage\n")
            types.appendText("object $enumResultClass {\n")

            classesList.forEach {
                val defs = (it as KSClassDeclaration).getDeclaredFunctions()
                for(def in defs) {
                    if(def.origin != Origin.SYNTHETIC) {
                        val named = def.simpleName.asString().toUpperSnakeCase()
                        types.appendText("    val $named = dev.epicpix.minecraftfunctioncompiler.il.InstructionType(\"$named\")\n")
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
    }
}

fun String.toUpperSnakeCase() = split(Regex("(?=[A-Z])")).joinToString("_").uppercase(Locale.getDefault())

data class InstructionParamData(val name: String, val isNullable: Boolean, val isList: Boolean, val isMap: Boolean, val isSource: Boolean, val isDestination: Boolean)

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}