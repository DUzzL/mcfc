package dev.epicpix.annotations

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.OutputStream
import java.util.*

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {
    private var invoked = false

    fun loadJson(file: String): JsonElement {
        val str = MainProcessor::class.java.getResourceAsStream("/data/$file.json")?.readAllBytes()?.decodeToString()
            ?: throw NullPointerException("Failed to read file $file.json")
        return Gson().fromJson(str, JsonElement::class.java)
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true

        generateFunctionMapping(resolver)
        generateInstructionData(resolver)

        return listOf()
    }

    fun generateFunctionMapping(resolver: Resolver) {
        val map = codeGenerator.createNewFile(Dependencies(true), "dev.epicpix.minecraftfunctioncompiler.emitter", "MappedData", "java")
        map.appendText("package dev.epicpix.minecraftfunctioncompiler.emitter;\n")

        map.appendText("public enum MappedData {\n")
        val defs = resolver.getClassDeclarationByName("dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions")!!.getDeclaredFunctions()
        for(def in defs) {
            if(def.isPrivate() && def.modifiers.contains(Modifier.JAVA_STATIC)) {
                map.appendText("    ${def.simpleName.asString()},\n")
            }
        }
        map.appendText("    ;\n")
        map.appendText("\n")
        map.appendText("    java.util.function.Consumer<dev.epicpix.minecraftfunctioncompiler.emitter.CodeWriter> generator = null;\n");
        map.appendText("    void generate(dev.epicpix.minecraftfunctioncompiler.emitter.CodeWriter cw) { generator.accept(cw); }\n")
        map.appendText("}\n")
        map.close()
    }

    fun generateInstructionData(resolver: Resolver) {
        val defs = resolver.getClassDeclarationByName("dev.epicpix.minecraftfunctioncompiler.il.InstructionDefinitions")!!.getDeclaredFunctions()

        val impl = codeGenerator.createNewFile(Dependencies(true), "dev.epicpix.minecraftfunctioncompiler.il", "InstructionsGen", "kt")
        impl.appendText("package dev.epicpix.minecraftfunctioncompiler.il\n")
        impl.appendText("data object Instructions {\n")

        val types = codeGenerator.createNewFile(Dependencies(true), "dev.epicpix.minecraftfunctioncompiler.il", "InstructionType", "java")
        types.appendText("package dev.epicpix.minecraftfunctioncompiler.il;\n")
        types.appendText("public enum InstructionType {\n")

        for(def in defs) {
            if(def.origin != Origin.SYNTHETIC) {
                val named = def.simpleName.asString().toUpperSnakeCase()
                types.appendText("    $named,\n")
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
                    val typeArgs = resolvedType.arguments.map { arg ->
                        arg.type?.resolve()?.declaration?.qualifiedName?.asString() ?: "*"
                    }.joinToString()

                    val fullTypeName = (if(typeArgs.isNotEmpty()) "$typeName<$typeArgs>" else typeName).run { if(resolvedType.isMarkedNullable) "$this?" else this }
                    impl.appendText("${param.name!!.asString()}: $fullTypeName, ")
                    params.add(InstructionParamData(
                        param.name!!.asString(),
                        resolvedType.isMarkedNullable,
                        resolvedType.declaration.qualifiedName!!.asString() == "kotlin.collections.List",
                        resolvedType.declaration.qualifiedName!!.asString() == "kotlin.collections.Map",
                        resolvedType.declaration.qualifiedName!!.asString() == "dev.epicpix.minecraftfunctioncompiler.il.InstructionData",
                        isSource,
                        isDestination))
                }
                val instructionEnumType = "dev.epicpix.minecraftfunctioncompiler.il.InstructionType.$named"
                val instructionType = "dev.epicpix.minecraftfunctioncompiler.il.Instruction"
                impl.appendText("): $instructionType = $instructionType($instructionEnumType, mapOf(")
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

        impl.appendText("}\n")
        impl.close()
        types.appendText("}\n")
        types.close()
    }
}

fun String.toUpperSnakeCase() = split(Regex("(?=[A-Z])")).joinToString("_").uppercase(Locale.getDefault())

data class InstructionParamData(val name: String, val isNullable: Boolean, val isList: Boolean, val isMap: Boolean, val isLocation: Boolean, val isSource: Boolean, val isDestination: Boolean)

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}