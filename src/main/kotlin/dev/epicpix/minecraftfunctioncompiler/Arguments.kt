package dev.epicpix.minecraftfunctioncompiler

import java.util.*

data class FloatRangeArgument(
    val minimum: Float?,
    val maximum: Float?
)
data class IntRangeArgument(
    val minimum: Int?,
    val maximum: Int?
)

data class ScoreHolderArgument(
    val all: Boolean,
    val name: String?,
    val selector: SelectorArgument?
)

data class SelectorArgument(
    val entityUuid: UUID? = null,
    val playerName: String? = null,
    val self: Boolean = false,
    val limit: Int? = null,
)