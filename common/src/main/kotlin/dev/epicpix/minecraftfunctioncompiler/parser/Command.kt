package dev.epicpix.minecraftfunctioncompiler.parser

data class Command(val type: CommandType, val params: Map<String, Any?>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(param: String) = params[param] as T
}

data class CommandType(val name: String) {
    override fun toString() = name
}