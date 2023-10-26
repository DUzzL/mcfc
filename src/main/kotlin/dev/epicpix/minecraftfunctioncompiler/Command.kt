package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any>, val sideEffects: List<SideEffect>)