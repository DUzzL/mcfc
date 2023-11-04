package dev.epicpix.minecraftfunctioncompiler.data

data class NegatableData<T>(
    val data: T,
    val negated: Boolean
)
