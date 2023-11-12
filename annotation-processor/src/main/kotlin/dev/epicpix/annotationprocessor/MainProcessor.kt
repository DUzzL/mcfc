package dev.epicpix.annotationprocessor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.Modifier
import java.io.OutputStream

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

class MainProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(invoked) return emptyList()
        invoked = true

        val maps = codeGenerator.createNewFile(Dependencies(true), "dev.epicpix.minecraftfunctioncompiler.emitter", "MappedData", "java")
        maps.appendText("package dev.epicpix.minecraftfunctioncompiler.emitter;\n")

        maps.appendText("public enum MappedData {\n")
        val defs = resolver.getClassDeclarationByName("dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions")!!.getDeclaredFunctions()
        for(def in defs) {
            if(def.isPrivate() && def.modifiers.contains(Modifier.JAVA_STATIC)) {
                maps.appendText("${def.simpleName.asString()},\n")
            }
        }
        maps.appendText(";\n")
        maps.appendText("java.util.function.Consumer<dev.epicpix.minecraftfunctioncompiler.emitter.CodeWriter> generator = null;\n");
        maps.appendText("void generate(dev.epicpix.minecraftfunctioncompiler.emitter.CodeWriter cw) { generator.accept(cw); }\n")
        maps.appendText("}\n")
        maps.close()

        return listOf()
    }
}

class MainProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MainProcessor(environment.codeGenerator, environment.options)
    }
}