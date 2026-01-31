package dev.epicpix.annotations.tests

data class ParsedTest(
    val test: TestInfo,
    val preContext: PreTestContext?,
    val postContext: PostTestContext?,
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

interface ControlInstruction {
    companion object {
        fun parse(line: String): ControlInstruction {
            TODO("no control instructions available")
        }
    }
}

class PreTestContext : TestContext<ControlInstruction>() {
    override fun addLine(line: String) = addParsed(ControlInstruction.parse(line))
}

class PostTestContext : TestContext<ControlInstruction>() {
    override fun addLine(line: String) = addParsed(ControlInstruction.parse(line))
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
            if(parts[0] == "pre") {
                val context = PreTestContext()
                currentContext = context
                contexts.add(context)
            }else if(parts[0] == "post") {
                val context = PostTestContext()
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
    return ParsedTest(test, contexts.filterIsInstance<PreTestContext>().firstOrNull(), contexts.filterIsInstance<PostTestContext>().firstOrNull(), contexts.filterIsInstance<FileTestContext>())
}
