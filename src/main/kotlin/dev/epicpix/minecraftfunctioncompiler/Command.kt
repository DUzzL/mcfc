package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any?>?, val sideEffects: MutableList<SideEffect>) {

    operator fun <T> get(param: String): T {
        return params?.get(param) as T
    }

}