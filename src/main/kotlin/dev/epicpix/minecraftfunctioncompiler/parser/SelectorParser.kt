package dev.epicpix.minecraftfunctioncompiler.parser

import com.mojang.brigadier.StringReader
import dev.epicpix.minecraftfunctioncompiler.data.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.GameType
import java.util.*

fun parseScoreHolder(scoreHolder: String): ScoreHolderArgument {
    if(scoreHolder.startsWith("@")) {
        return ScoreHolderArgument(false, null, parseSelector(scoreHolder))
    }
    if(scoreHolder == "*") {
        return ScoreHolderArgument(true, null, null)
    }
    return ScoreHolderArgument(false, scoreHolder, null)
}

fun parseSelector(selector: String): SelectorArgument {
    if(!selector.startsWith("@")) {
        val entityUuid = runCatching { UUID.fromString(selector) }
        if (entityUuid.isSuccess) {
            return SelectorArgument(entityUuid = entityUuid.getOrThrow())
        }
        return SelectorArgument(playerName = selector)
    }

    val reader = StringReader(selector)
    reader.skip()
    val selectorType = reader.read()

    val isSelf = selectorType == 's'
    val data = SelectorGeneratorData()
    if(isSelf) data.limit = 1
    if(selectorType == 'a') data.entityTypes = NegatableData(data = listOf(ResourceLocation("minecraft", "player")), negated = false)

    if(selectorType == 'r' || selectorType == 'p') throw UnsupportedOperationException("Unsupported selector: $selector")

    reader.skipWhitespace()
    if(reader.canRead() && reader.read() == '[') {
        while(reader.canRead()) {
            val key = reader.readString()
            reader.skipWhitespace()
            if(!reader.canRead() || reader.read() != '=') throw UnsupportedOperationException("Cannot parse selector, missing '=': $selector")
            reader.skipWhitespace()
            parseArgumentSelectorOption(reader, key, selector, data)
            reader.skipWhitespace()
            if(!reader.canRead()) throw UnsupportedOperationException("Cannot parse selector, ran out of data: $selector")
            val next = reader.read()
            if(next == ',') continue
            if(next == ']') break
            throw UnsupportedOperationException("Cannot parse selector, ran out of data: $selector")
        }
    }

    return SelectorArgument(
        self = isSelf,
        entityTypes = data.entityTypes,
        tags = data.tags,
        hasTags = if(data.tags.isNotEmpty()) null else data.hasTags,
        team = data.team,
        hasTeam = if(data.team != null) null else data.hasTeam,
        limit = data.limit,
        scores = data.scores,
        gamemode = data.gamemode,
    )
}

private fun parseArgumentSelectorOption(reader: StringReader, key: String, selector: String, data: SelectorGeneratorData) {
    when(key) {
        "tag" -> {
            val negated = if(reader.peek() == '!') { reader.read(); true } else false
            val tag = reader.readUnquotedString()
            if(tag == "") {
                data.hasTags = !negated
            }else {
                data.tags.add(NegatableData(tag, negated))
            }
        }
        "limit" -> {
            data.limit = reader.readInt()
        }
        "scores" -> {
            reader.skip()
            reader.skipWhitespace()
            while(reader.peek() != '}') {
                val objective = reader.readUnquotedString()
                reader.skipWhitespace()
                reader.skip()
                reader.skipWhitespace()
                var start: Int? = null
                var end: Int? = null
                if(reader.peek().isDigit() || reader.peek() == '-') start = readInt(reader)
                if(reader.peek() == '.') {
                    reader.skip()
                    reader.skip()
                    if(reader.peek().isDigit() || reader.peek() == '-') {
                        end = readInt(reader)
                    }
                } else end = start
                reader.skipWhitespace()
                if(reader.peek() == ',') reader.read()
                data.scores[objective] = IntRangeArgument(start, end)
            }
            reader.read()
        }
        "type" -> {
            val negated = if(reader.peek() == '!') { reader.read(); true } else false
            val tag = if(reader.peek() == '#') { reader.read(); true } else false
            val type = ResourceLocation.read(reader)
            val types = if(!tag) listOf(type) else {
                val tagKey = TagKey.create(Registries.ENTITY_TYPE, type)
                val typeData = BuiltInRegistries.ENTITY_TYPE.getTag(tagKey)
                if(typeData.isEmpty) {
                    listOf()
                }else {
                    BuiltInRegistries.ENTITY_TYPE.getTag(tagKey).get().map { it.unwrapKey().get().location() }.toList()
                }
            }
            types.forEach {
                if(mapEntityTypeClass(it) == null) throw UnsupportedOperationException("Unsupported entity type: $it")
            }
            data.entityTypes = NegatableData(types, negated)
        }
        "gamemode" -> {
            val negated = if(reader.peek() == '!') { reader.read(); true } else false
            data.gamemode = NegatableData(GameType.byName(reader.readUnquotedString(), null)!!, negated)
            data.entityTypes = NegatableData(listOf(ResourceLocation("minecraft", "player")), false)
        }
        "team" -> {
            val negated = if(reader.peek() == '!') { reader.read(); true } else false
            val team = reader.readUnquotedString()
            if(team == "") {
                data.hasTeam = !negated
            }else {
                data.team = NegatableData(team, negated)
            }
        }
        else -> throw UnsupportedOperationException("Cannot parse selector key: $key in $selector")
    }
}

private fun readInt(stringReader: StringReader): Int {
    var num = 0
    val negative: Boolean = stringReader.peek() == '-'
    if(negative) stringReader.read()
    while(stringReader.canRead()) {
        if(stringReader.peek().isDigit()) {
            num = num * 10 + (stringReader.read() - '0')
        }else break
    }
    return if(negative) -num else num
}

data class SelectorGeneratorData(
    var entityTypes: NegatableData<List<ResourceLocation>>? = null,
    val tags: MutableList<NegatableData<String>> = mutableListOf(),
    var hasTags: Boolean? = null,
    var team: NegatableData<String>? = null,
    var hasTeam: Boolean? = null,
    var limit: Int? = null,
    val scores: MutableMap<String, IntRangeArgument> = mutableMapOf(),
    var gamemode: NegatableData<GameType>? = null,
)