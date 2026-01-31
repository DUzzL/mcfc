package dev.epicpix.annotations.tests

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import dev.epicpix.annotations.appendText

fun generateTests(codeGenerator: CodeGenerator, tests: List<TestInfo>) {
    run {
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.test.tests", "package-info", "java")
        map.appendText("@org.jspecify.annotations.NullMarked\n")
        map.appendText("package dev.epicpix.minecraftfunctioncompiler.test.tests;\n")
        map.close()
    }

    run {
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.test.tests", "Tests", "java")
        map.appendText("package dev.epicpix.minecraftfunctioncompiler.test.tests;\n")
        map.appendText("public final class Tests {\n")
        map.appendText("    private Tests() {}\n")
        map.appendText("    public static final java.util.List<dev.epicpix.minecraftfunctioncompiler.test.CompiledTest> TESTS = java.util.Collections.unmodifiableList(java.util.List.of());\n")
        map.appendText("}\n")
        map.close()
    }
}