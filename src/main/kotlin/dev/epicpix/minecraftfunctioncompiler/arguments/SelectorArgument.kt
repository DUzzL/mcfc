package dev.epicpix.minecraftfunctioncompiler.arguments

import java.util.UUID

data class SelectorArgument(
    val entityUuid: UUID? = null,
    val playerName: String? = null,
)