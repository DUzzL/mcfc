package dev.epicpix.minecraftfunctioncompiler.data

import java.util.*

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
    val selector: SelectorArgument?,
    val single: Boolean = false,
) {
    fun single() = copy(single = true)
}

data class SelectorArgument(
    val entityUuid: UUID? = null,
    val playerName: String? = null,
    val self: Boolean = false,
    val limit: Int? = null,
    val entityTypes: NegatableData<List<DataLocation>>? = null,
    val hasTags: Boolean? = null,
    val tags: List<NegatableData<String>> = listOf(),
    val hasTeam: Boolean? = null,
    val team: NegatableData<String>? = null,
    val scores: Map<String, IntRangeArgument> = mapOf(),
    val gamemode: NegatableData<Gamemode>? = null,
    val selectPlayers: Boolean = entityTypes != null && !entityTypes.negated && entityTypes.data.size == 1 && entityTypes.data[0] == DataLocation("minecraft", "player"),
)

data class WorldCoordinates(
    val x: Double,
    val isXRelative: Boolean,
    val y: Double,
    val isYRelative: Boolean,
    val z: Double,
    val isZRelative: Boolean,
)

data class SwizzleArgument(
    val x: Boolean,
    val y: Boolean,
    val z: Boolean,
)