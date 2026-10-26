package dev.epicpix.annotations.tests

data class ParsedTest(
    val test: TestInfo,
    val setupContext: SetupTestContext?,
    val validateContext: ValidateTestContext?,
    val filesContext: List<FileTestContext>,
)

abstract class TestContext<out T> {
    private val parsed: MutableList<T> = mutableListOf()

    fun addParsed(element: @UnsafeVariance T) {
        parsed.add(element)
    }

    fun getParsed(): List<T> = parsed

    abstract fun addLine(line: String)
}

interface SetupInstruction {
    companion object {
        fun parse(line: String): SetupInstruction {
            val parts = line.split(" ")
            if(parts[0] == "objective") {
                if(parts[1] == "create") {
                    return CreateScoreboardInstruction(parts[2], parts[3])
                }
            }
            throw IllegalArgumentException("invalid setup instruction: $line")
        }
    }
}

interface ValidateInstruction {
    companion object {
        fun parse(line: String): ValidateInstruction {
            val parts = line.split(" ")
            if(parts[0] == "no_fallback" && parts.size == 1) {
                return NoFallbackInstruction
            }
            if(parts[0] == "score") {
                if(parts[3] == "is") {
                    return CheckScoreInstruction(parts[2], parts[1], parts[4].toInt())
                }
            }
            throw IllegalArgumentException("invalid validation instruction: $line")
        }
    }
}

data class CreateScoreboardInstruction(val name: String, val criterion: String) : SetupInstruction

data class CheckScoreInstruction(val objective: String, val holder: String, val value: Int) : ValidateInstruction

/** Every command of the test compiled: the compiler reported no diagnostic (no vanilla fallback). */
data object NoFallbackInstruction : ValidateInstruction

class SetupTestContext : TestContext<SetupInstruction>() {
    override fun addLine(line: String) = addParsed(SetupInstruction.parse(line))
}

class ValidateTestContext : TestContext<ValidateInstruction>() {
    override fun addLine(line: String) = addParsed(ValidateInstruction.parse(line))
}

class FileTestContext(val fileLocation: String) : TestContext<String>() {
    override fun addLine(line: String) = addParsed(line)
}

fun parseTest(test: TestInfo): ParsedTest {
    val lines = test.file.readLines()
    val contexts = mutableListOf<TestContext<Any>>()
    var currentContext: TestContext<Any>? = null
    for(line in lines) {
        val line = line.trim()
        if(line.isEmpty()) continue
        if(line.startsWith(';')) continue

        if(line.startsWith("@")) {
            val parts = line.substring(1).split(" ")
            if(parts[0] == "setup") {
                val context = SetupTestContext()
                currentContext = context
                contexts.add(context)
            }else if(parts[0] == "validate") {
                val context = ValidateTestContext()
                currentContext = context
                contexts.add(context)
            }else if(parts[0] == "function") {
                val name = parts[1]
                val location = if(name.contains(":")) {
                    val locationParts = name.split(":", limit = 2)
                    "${locationParts[0]}:function/${locationParts[1]}.mcfunction"
                }else {
                    "mcfc-test:function/$name.mcfunction"
                }
                val context = FileTestContext(location)
                currentContext = context
                contexts.add(context)
            }else if(parts[0] == "file") {
                // raw datapack resource, e.g. `@file mcfc-test:tags/function/all.json`
                val context = FileTestContext(parts[1])
                currentContext = context
                contexts.add(context)
            }else {
                throw IllegalArgumentException("invalid compiler instruction \"${line}\", during parsing test ${test.id}")
            }
        }else {
            if(currentContext == null) {
                throw IllegalStateException("no context currently bound, during parsing test ${test.id}")
            }
            currentContext.addLine(line)
        }
    }
    return ParsedTest(test, contexts.filterIsInstance<SetupTestContext>().firstOrNull(), contexts.filterIsInstance<ValidateTestContext>().firstOrNull(), contexts.filterIsInstance<FileTestContext>())
}
