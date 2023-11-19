package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData

data class SelectorGeneratorData(
    var entityTypes: NegatableData<List<DataLocation>>? = null,
    val tags: MutableList<NegatableData<String>> = mutableListOf(),
    var hasTags: Boolean? = null,
    var team: NegatableData<String>? = null,
    var hasTeam: Boolean? = null,
    var limit: Int? = null,
    val scores: MutableMap<String, IntRangeArgument> = mutableMapOf(),
    var gamemode: NegatableData<Gamemode>? = null,
)