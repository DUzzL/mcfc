package dev.epicpix.minecraftfunctioncompiler.parser

import com.mojang.brigadier.StringReader
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
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
    if(selectorType == 'a') data.entityType = "minecraft:player"

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

    return SelectorArgument(self = isSelf, entityType = data.entityType, tags = data.tags.toList(), hasTags = if(data.tags.isNotEmpty()) null else data.hasTags, limit = data.limit)
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
        else -> throw UnsupportedOperationException("Cannot parse selector key: $key in $selector")
    }
}

data class SelectorGeneratorData(
    var entityType: String? = null,
    val tags: MutableList<NegatableData<String>> = mutableListOf(),
    var hasTags: Boolean? = null,
    var limit: Int? = null,
)