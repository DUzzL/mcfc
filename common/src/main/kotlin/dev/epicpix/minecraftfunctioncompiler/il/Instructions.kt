package dev.epicpix.minecraftfunctioncompiler.il

import java.util.function.Consumer

enum class ScoreboardOperation { SET, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, MIN, MAX, SWAP }
enum class ScoreboardCompareOperation { LT, LE, EQ, NE, GE, GT }

data class LocationData(
    val local: Int? = null,
    val string: String? = null,
    val integer: Int? = null,

    val isSource: Boolean = false,
    val isDestination: Boolean = false,
) {
    fun source() = uses(source = true, destination = false)
    fun destination() = uses(source = false, destination = true)
    fun uses(source: Boolean, destination: Boolean) = LocationData(local, string, integer, isSource = source, isDestination = destination)

    override fun toString(): String {
        return "${if(local != null) "local " else ""}${if(isSource) "<in> " else ""}${if(isDestination) "<out> " else ""}${local ?: ""}${if(string != null) "\"${string.replace("\\", "\\\\").replace("\n", "\\n")}\"" else ""}${integer?.toString() ?: ""}"
    }
}

data class ScopeData(
    val id: Int,

    val isSource: Boolean = false,
    val isDestination: Boolean = false,
) {
    fun source() = uses(source = true, destination = false)
    fun destination() = uses(source = false, destination = true)
    fun uses(source: Boolean, destination: Boolean) = ScopeData(id, isSource = source, isDestination = destination)

    override fun toString(): String {
        return "scope ${if(isSource) "<in> " else ""}${if(isDestination) "<out> " else ""}$id"
    }
}

data class Instruction(val type: InstructionType, val data: Map<String, Any?>, val children: MutableList<Instruction> = mutableListOf()) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T = data[key] as T

    fun with(instruction: Instruction): Instruction {
        children.add(instruction)
        return this
    }
    fun withChildren(children: MutableList<Instruction>): Instruction {
        return copy(children = children)
    }
    fun countInstructions(): Int = 1 + children.sumOf { it.countInstructions() }
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

fun printInstructionList(instructions: List<Instruction>, log: Consumer<String>, currentDepth: Int) {
    for(instruction in instructions) {
        log.accept("-".repeat(currentDepth) + instruction.type + " - " + instruction.data)
        printInstructionList(instruction.children, log, currentDepth + 1)
    }
}