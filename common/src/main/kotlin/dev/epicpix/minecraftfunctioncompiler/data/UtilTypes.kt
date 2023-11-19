package dev.epicpix.minecraftfunctioncompiler.data

data class NegatableData<T>(
    val data: T,
    val negated: Boolean
)

data class DataLocation(val namespace: String, val path: String)

enum class Gamemode {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR,
}
