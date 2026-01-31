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
        for(test in tests) {
            val parsed = parseTest(test)
            val name = test.javaFileName()
            val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.test.tests", name, "java")
            map.appendText("package dev.epicpix.minecraftfunctioncompiler.test.tests;\n")
            map.appendText("public final class $name implements dev.epicpix.minecraftfunctioncompiler.test.CompiledTest {\n")
            map.appendText("    $name() {}\n")
            map.appendText("\n")
            map.appendText("    private static final java.util.List<dev.epicpix.minecraftfunctioncompiler.test.TestFile> FILES = java.util.List.of(\n")
            var index = 0
            for(file in parsed.filesContext) {
                val location = file.fileLocation.split(":", limit = 2)
                val content = file.getParsed().joinToString("\n").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
                if(tests.size == ++index) {
                    map.appendText("    new dev.epicpix.minecraftfunctioncompiler.test.TestFile(new dev.epicpix.minecraftfunctioncompiler.data.DataLocation(\"${location[0]}\", \"${location[1]}\"), \"$content\")\n")
                }else {
                    map.appendText("    new dev.epicpix.minecraftfunctioncompiler.test.TestFile(new dev.epicpix.minecraftfunctioncompiler.data.DataLocation(\"${location[0]}\", \"${location[1]}\"), \"$content\"),\n")
                }
            }
            map.appendText("    );\n")
            map.appendText("\n")
            map.appendText("    @Override\n")
            map.appendText("    public dev.epicpix.minecraftfunctioncompiler.data.DataLocation id() {\n")
            map.appendText("        return new dev.epicpix.minecraftfunctioncompiler.data.DataLocation(\"mcfc-test\", \"${test.id}\");\n")
            map.appendText("    }\n")
            map.appendText("\n")
            map.appendText("    @Override\n")
            map.appendText("    public java.util.List<dev.epicpix.minecraftfunctioncompiler.test.TestFile> files() {\n")
            map.appendText("        return FILES;\n")
            map.appendText("    }\n")
            map.appendText("}\n")
        }
    }

    run {
        val map = codeGenerator.createNewFile(Dependencies.ALL_FILES, "dev.epicpix.minecraftfunctioncompiler.test.tests", "Tests", "java")
        map.appendText("package dev.epicpix.minecraftfunctioncompiler.test.tests;\n")
        map.appendText("public final class Tests {\n")
        map.appendText("    private Tests() {}\n")
        map.appendText("    public static final java.util.List<dev.epicpix.minecraftfunctioncompiler.test.CompiledTest> TESTS = java.util.List.of(\n")
        var index = 0
        for(test in tests) {
            if(tests.size == ++index) {
                map.appendText("        new ${test.javaFileName()}()\n")
            }else {
                map.appendText("        new ${test.javaFileName()}(),\n")
            }
        }
        map.appendText("    );\n")
        map.appendText("}\n")
        map.close()
    }
}
