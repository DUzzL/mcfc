package dev.epicpix.minecraftfunctioncompiler.data

import java.util.*

data class FloatRangeArgument(
    val minimum: Float?,
    val maximum: Float?
)

data class IntRangeArgument(
    val minimum: Int?,
    val maximum: Int?
) {
    override fun toString(): String {
        return "${minimum ?: ""}..${maximum ?: ""}"
    }
}

data class ScoreHolderArgument(
    val all: Boolean,
    val name: String?,
    val selector: SelectorArgument?
) {
    fun single(): ScoreHolderArgument {
        return if(selector != null) ScoreHolderArgument(false, null, selector.copy(limit = 1))
        else this
    }
}

data class SelectorArgument(
    val entityUuid: UUID? = null,
    val playerName: String? = null,
    val self: Boolean = false,
    val limit: Int? = null,
    val entityType: String? = null,
    val hasTags: Boolean? = null,
    val tags: List<NegatableData<String>> = listOf(),
)

data class WorldCoordinates(
    val x: Double,
    val isXRelative: Boolean,
    val y: Double,
    val isYRelative: Boolean,
    val z: Double,
    val isZRelative: Boolean,
)