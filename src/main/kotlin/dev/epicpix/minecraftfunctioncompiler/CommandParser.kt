package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedCommandNode
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.util.*

object CommandParser {

    fun parseFunctionCommands(func: CommandFunction, server: MinecraftServer): List<CommandLike> {
        val commands = mutableListOf<CommandLike>()
        for (entry in func.entries) {
            if (entry !is CommandFunction.CommandEntry) continue

            val context = entry.parse.context
            val cmdName = context.nodes[0].node.name

            val parsed = parseCommand(cmdName, context, entry.parse.reader.string, server)
            if(parsed is Command) commands.add(parsed.toCommandBlock())
            else commands.add(parsed)
        }
        return commands
    }

    private fun parseCommand(cmdName: String, context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer): CommandLike {
        return when(cmdName) {
            "execute" -> parseCommandExecute(context, context.nodes.subList(1, context.nodes.size), source, server)
            "function" -> parseCommandFunction(context, source, server)
            "kill" -> parseCommandKill(context, source)
            "scoreboard" -> parseCommandScoreboard(context, source)
            "tag" -> parseCommandTag(context, source)
            else -> throw UnsupportedOperationException("Cannot parse command: $cmdName")
        }
    }

    private fun parseCommandExecute(context: CommandContextBuilder<CommandSourceStack>, nodes: List<ParsedCommandNode<CommandSourceStack>>, source: String, server: MinecraftServer): CommandLike {
        val type = nodes[0].range[source]
        return when(type) {
            "as" -> {
                Command(CommandType.EXECUTE_AS, mapOf(
                    "targets" to parseArgumentSelector(context.arguments["targets"]!!.range[source]),
                    "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                ), mutableListOf())
            }
            "if" -> {
                val ifType = nodes[1].range[source]
                when(ifType) {
                    "entity" -> {
                        Command(CommandType.EXECUTE_IF_ENTITY, mapOf(
                            "entities" to parseArgumentSelector(context.arguments["entities"]!!.range[source]),
                            "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                        ), mutableListOf())
                    }
                    "score" -> {
                        val comparison = nodes[4].range[source]
                        if(comparison == "matches") {
                            val range = context.arguments["range"]!!.result as MinMaxBounds.Ints
                            Command(CommandType.EXECUTE_IF_SCORE_RANGE, mapOf(
                                "target" to parseArgumentScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "range" to IntRangeArgument(range.min.orElse(null), range.max.orElse(null)),
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ), mutableListOf())
                        }else {
                            Command(CommandType.EXECUTE_IF_SCORE_COMPARE, mapOf(
                                "target" to parseArgumentScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "comparison" to comparison,
                                "source" to parseArgumentScoreHolder(context.arguments["source"]!!.range[source]),
                                "sourceObjective" to context.arguments["sourceObjective"]!!.result as String,
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ), mutableListOf())
                        }
                    }
                    else -> throw UnsupportedOperationException("Invalid execute command if comparison type: $ifType")
                }
            }
            "run" -> parseCommand(context.child.nodes[0].range[source], context.child, source, server)
            "unless" -> {
                val ifType = nodes[1].range[source]
                throw UnsupportedOperationException("Invalid execute command unless comparison type: $ifType")
            }
            else -> throw UnsupportedOperationException("Invalid execute command type: $type")
        }
    }

    private fun parseCommandFunction(context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer): CommandLike {
        if(context.nodes.size > 2) {
            throw UnsupportedOperationException("Too many function arguments, not supported")
        }
        val name = context.arguments["name"]!!.range[source]
        return if(name.startsWith("#")) {
            Command(CommandType.FUNCTION, mapOf("commands" to server.functions.getTag(ResourceLocation(name.substring(1))).toList()), mutableListOf())
        }else {
            Command(CommandType.FUNCTION, mapOf("commands" to listOf(ResourceLocation(name))), mutableListOf())
        }
    }

    private fun parseCommandKill(context: CommandContextBuilder<CommandSourceStack>, source: String): CommandLike {
        val targets = context.arguments["targets"]
        return if(targets != null) {
            Command(CommandType.KILL, mapOf("targets" to parseArgumentSelector(targets.range[source])), mutableListOf())
        }else {
            Command(CommandType.KILL, mapOf("targets" to SelectorArgument(self = true)), mutableListOf())
        }
    }

    private fun parseCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, source: String): CommandLike {
        val type = context.nodes[1].node.name
        return when(type) {
            "players" -> {
                val opType = context.nodes[2].node.name
                when(opType) {
                    "add" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_ADD, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    "operation" -> {
                        val targetObjective = context.arguments["targetObjective"]!!.result as String
                        val sourceObjective = context.arguments["sourceObjective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_OPERATION, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "targetObjective" to targetObjective,
                            "operation" to context.arguments["operation"]!!.range[source] as String,
                            "source" to parseArgumentScoreHolder(context.arguments["source"]!!.range[source]),
                            "sourceObjective" to sourceObjective,
                        ), mutableListOf())
                    }
                    "remove" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_REMOVE, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    "reset" -> {
                        if(context.arguments["objective"] != null) {
                            val objective = context.arguments["objective"]!!.result as String
                            Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                                "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                                "objective" to objective
                            ), mutableListOf())
                        }else {
                            Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                                "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source])
                            ), mutableListOf())
                        }
                    }
                    "set" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_SET, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: $opType")
                }
            }
            else -> throw UnsupportedOperationException("Invalid scoreboard command type: $type")
        }
    }

    private fun parseCommandTag(context: CommandContextBuilder<CommandSourceStack>, source: String): CommandLike {
        val targets = parseArgumentSelector(context.arguments["targets"]!!.range[source])
        val type = context.nodes[2].node.name
        return when(type) {
            "add" -> {
                Command(CommandType.TAG_ADD, mapOf(
                    "targets" to targets,
                    "name" to context.arguments["name"]!!.result as String
                ), mutableListOf())
            }
            "remove" -> {
                Command(CommandType.TAG_REMOVE, mapOf(
                    "targets" to targets,
                    "name" to context.arguments["name"]!!.result as String
                ), mutableListOf())
            }
            else -> throw UnsupportedOperationException("Invalid tag command type: $type")
        }
    }

    private fun parseArgumentScoreHolder(scoreHolder: String): ScoreHolderArgument {
        if(scoreHolder.startsWith("@")) {
            return ScoreHolderArgument(false, null, parseArgumentSelector(scoreHolder))
        }
        if(scoreHolder == "*") {
            return ScoreHolderArgument(true, null, null)
        }
        return ScoreHolderArgument(false, scoreHolder, null)
    }

    private fun parseArgumentSelector(selector: String): SelectorArgument {
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

        return SelectorArgument(self = isSelf, entityType = data.entityType, tags = data.tags.toList(), hasTags = data.hasTags, limit = data.limit)
    }

    private fun parseArgumentSelectorOption(reader: StringReader, key: String, selector: String, data: SelectorGeneratorData) {
        when(key) {
            "tag" -> {
                val negated = if(reader.peek() == '!') { reader.read(); true } else false
                val tag = reader.readUnquotedString()
                if(tag == "") {
                    data.hasTags = negated
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

}

data class SelectorGeneratorData(
    var entityType: String? = null,
    val tags: MutableList<NegatableData<String>> = mutableListOf(),
    var hasTags: Boolean? = null,
    var limit: Int? = null,
)