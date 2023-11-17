package dev.epicpix.minecraftfunctioncompiler.il

data class LocationData(
    val local: Int? = null,
    val string: String? = null,
    val integer: Int? = null,

    val isSource: Boolean = false,
    val isDestination: Boolean = false,
) {
    fun source() = LocationData(local, string, integer, isSource = true, isDestination = false)
    fun destination() = LocationData(local, string, integer, isSource = false, isDestination = true)

    override fun toString(): String {
        return "${if(local != null) "local " else ""}${if(isSource) "<in> " else ""}${if(isDestination) "<out> " else ""}${local ?: ""}${if(string != null) "\"${string.replace("\\", "\\\\").replace("\n", "\\n")}\"" else ""}${integer?.toString() ?: ""}"
    }
}

data class ScopeData(
    val id: Int,

    val isSource: Boolean = false,
    val isDestination: Boolean = false,
) {
    fun source() = ScopeData(id, isSource = true, isDestination = false)
    fun destination() = ScopeData(id, isSource = false, isDestination = true)

    override fun toString(): String {
        return "scope ${if(isSource) "<in> " else ""}${if(isDestination) "<out> " else ""}$id"
    }
}

enum class ScoreboardOperation { SET, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, MIN, MAX, SWAP }
enum class ScoreboardCompareOperation { LT, LE, EQ, NE, GE, GT }

data class Instruction(val type: InstructionType, val data: Map<String, Any?>, val children: MutableList<Instruction> = mutableListOf()) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T = data[key] as T

    fun with(instruction: Instruction): Instruction {
        children.add(instruction)
        return this
    }
    fun countInstructions(): Int {
        var result = 1
        children.forEach { result += it.countInstructions() }
        return result
    }
}

fun remapInstructionData(data: Map<String, Any?>, mappings: Map<Int, Int>): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    data.forEach { (key, value) ->
        if(value is LocationData) {
            if(value.local != null) {
                result[key] = LocationData(mappings[value.local] ?: value.local, value.string, value.integer, value.isSource, value.isDestination)
                return@forEach
            }
        }
        result[key] = value
    }
    return result
}
