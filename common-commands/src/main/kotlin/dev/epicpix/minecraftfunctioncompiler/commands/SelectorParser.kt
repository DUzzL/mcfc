package dev.epicpix.minecraftfunctioncompiler.commands

import com.mojang.brigadier.StringReader
import dev.epicpix.minecraftfunctioncompiler.data.*
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
    if(selectorType == 'a') data.entityTypes = NegatableData(data = listOf(DataLocation("minecraft", "player")), negated = false)

    if(selectorType == 'r' || selectorType == 'p') throw UnsupportedOperationException("Unsupported selector: $selector")

    reader.skipWhitespace()
    if(reader.canRead() && reader.read() == '[') {
        while(reader.canRead()) {
            reader.skipWhitespace()
            val key = reader.readString()
            reader.skipWhitespace()
            if(!reader.canRead() || reader.read() != '=') throw UnsupportedOperationException("Cannot parse selector, missing '=': $selector")
            reader.skipWhitespace()
            val option = selectorOptions[key] ?: throw UnsupportedOperationException("Cannot parse selector key: $key in $selector")
            option(reader, data)
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


private val selectorOptions = mutableMapOf<String, StringReader.(SelectorGeneratorData) -> Unit>()
fun registerSelectorOption(name: String, exec: StringReader.(SelectorGeneratorData) -> Unit) { selectorOptions[name] = exec }

fun initBaseSelectorOptions() {
    registerSelectorOption("tag") { data ->
        val negated = if(peek() == '!') { read(); true } else false
        val tag = readUnquotedString()
        if(tag == "") {
            data.hasTags = !negated
        }else {
            data.tags.add(NegatableData(tag, negated))
        }
    }
    registerSelectorOption("limit") { data ->
        data.limit = readInt()
    }
    registerSelectorOption("scores") { data ->
        skip()
        skipWhitespace()
        while(peek() != '}') {
            val objective = readUnquotedString()
            skipWhitespace()
            skip()
            skipWhitespace()
            var start: Int? = null
            var end: Int? = null
            if(peek().isDigit() || peek() == '-') start = readRawInt()
            if(peek() == '.') {
                skip()
                skip()
                if(peek().isDigit() || peek() == '-') {
                    end = readRawInt()
                }
            } else end = start
            skipWhitespace()
            if(peek() == ',') read()
            data.scores[objective] = IntRangeArgument(start, end)
        }
        read()
    }
    registerSelectorOption("team") { data ->
        val negated = if(peek() == '!') { read(); true } else false
        val team = readUnquotedString()
        if(team == "") {
            data.hasTeam = !negated
        }else {
            data.team = NegatableData(team, negated)
        }
    }
}