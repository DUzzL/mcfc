package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any?>?, val sideEffects: MutableList<SideEffect>) {

    operator fun get(param: String): Any? {
        return params?.get(param)
    }

}