package dev.epicpix.minecraftfunctioncompiler.commands

import com.mojang.brigadier.StringReader
import dev.epicpix.minecraftfunctioncompiler.data.*
import java.util.*

fun parseScoreHolder(scoreHolder: String): ScoreHolderArgument {
    if(scoreHolder.startsWith("@")) {
        return ScoreHolderArgument.selectSelector(parseSelector(scoreHolder))
    }
    if(scoreHolder == "*") {
        return ScoreHolderArgument.selectAll()
    }
    return ScoreHolderArgument.selectNamed(scoreHolder)
}

fun parseSelector(selector: String): SelectorArgument {
    if(!selector.startsWith("@")) {
        val entityUuid = runCatching { UUID.fromString(selector) }
        if (entityUuid.isSuccess) {
            return SelectorArgument.ofUuid(entityUuid.getOrThrow())
        }
        return SelectorArgument.ofPlayer(selector)
    }

    val reader = StringReader(selector)
    reader.skip()
    val selectorType = reader.read()

    val isSelf = selectorType == 's'
    val data = SelectorGeneratorBuilderData()
    if(isSelf) data.limit = 1
    if(selectorType == 'a') data.entityTypes = NegatableData(listOf(DataLocation("minecraft", "player")), false)

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
            option.accept(reader, data)
            reader.skipWhitespace()
            if(!reader.canRead()) throw UnsupportedOperationException("Cannot parse selector, ran out of data: $selector")
            val next = reader.read()
            if(next == ',') continue
            if(next == ']') break
            throw UnsupportedOperationException("Cannot parse selector, ran out of data: $selector")
        }
    }

    return data.build()
}


private val selectorOptions = mutableMapOf<String, SelectorReader>()
fun registerSelectorOption(name: String, exec: SelectorReader) { selectorOptions[name] = exec }

fun initBaseSelectorOptions() {
    registerSelectorOption("tag") { reader, data ->
        val negated = if(reader.peek() == '!') { reader.read(); true } else false
        val tag = reader.readUnquotedString()
        if(tag == "") {
            data.hasTags = !negated
        }else {
            data.tags.add(NegatableData(tag, negated))
        }
    }
    registerSelectorOption("limit") { reader, data ->
        data.limit = reader.readInt()
    }
    registerSelectorOption("scores") { reader, data ->
        reader.skip()
        reader.skipWhitespace()
        while(reader.peek() != '}') {
            val objective = reader.readUnquotedString()
            reader.skipWhitespace()
            reader.skip()
            reader.skipWhitespace()
            var start: Int? = null
            var end: Int? = null
            if(reader.peek().isDigit() || reader.peek() == '-') start = reader.readRawInt()
            if(reader.peek() == '.') {
                reader.skip()
                reader.skip()
                if(reader.peek().isDigit() || reader.peek() == '-') {
                    end = reader.readRawInt()
                }
            } else end = start
            reader.skipWhitespace()
            if(reader.peek() == ',') reader.read()
            data.scores[objective] = IntRangeArgument(start, end)
        }
        reader.read()
    }
    registerSelectorOption("team") { reader, data ->
        val negated = if(reader.peek() == '!') { reader.read(); true } else false
        val team = reader.readUnquotedString()
        if(team == "") {
            data.hasTeam = !negated
        }else {
            data.team = NegatableData(team, negated)
        }
    }
}