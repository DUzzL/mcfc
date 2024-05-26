package dev.epicpix.minecraftfunctioncompiler.v1_20_2.commands

import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedCommandNode
import dev.epicpix.minecraftfunctioncompiler.commands.CommandType
import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser.parseScoreHolder
import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser.parseSelector
import dev.epicpix.minecraftfunctioncompiler.data.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.toDataLocation
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.toGamemode
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.coordinates.LocalCoordinates
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import net.minecraft.world.level.GameType
import java.util.*

object CommandParser {

    fun parseFunctionCommands(func: CommandFunction, server: MinecraftServer, functionManager: ServerFunctionManager): List<Command> {
        val commands = mutableListOf<Command>()
        for (entry in func.entries) {
            if (entry !is CommandFunction.CommandEntry) continue

            val context = entry.parse.context
            val cmdName = context.nodes[0].node.name

            commands.add(parseCommand(cmdName, context, entry.parse.reader.string, server, functionManager))
        }
        return commands
    }

    private fun parseCommand(cmdName: String, context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer, functionManager: ServerFunctionManager): Command {
        return when(cmdName) {
            "execute" -> parseCommandExecute(context, context.nodes.subList(1, context.nodes.size), source, server, functionManager)
            "function" -> parseCommandFunction(context, source, functionManager)
            "gamemode" -> parseCommandGamemode(context, source)
            "kill" -> parseCommandKill(context, source)
            "scoreboard" -> parseCommandScoreboard(context, source)
            "tag" -> parseCommandTag(context, source)
            else -> throw UnsupportedOperationException("Cannot parse command: $cmdName")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCommandExecute(context: CommandContextBuilder<CommandSourceStack>, nodes: List<ParsedCommandNode<CommandSourceStack>>, source: String, server: MinecraftServer, functionManager: ServerFunctionManager): Command {
        return when(val type = nodes[0].range[source]) {
            "align" -> {
                val swizzle = context.arguments["axes"]!!.result as EnumSet<Direction.Axis>

                Command(CommandType.EXECUTE_ALIGN, mapOf(
                    "swizzle" to SwizzleArgument(swizzle.contains(Direction.Axis.X), swizzle.contains(Direction.Axis.Y), swizzle.contains(Direction.Axis.Z)),
                    "command" to getExecuteSubcommand(context, source, server, functionManager)
                ))
            }
            "as" -> {
                Command(CommandType.EXECUTE_AS, mapOf(
                    "targets" to parseSelector(context.arguments["targets"]!!.range[source]),
                    "command" to getExecuteSubcommand(context, source, server, functionManager)
                ))
            }
            "at" -> {
                Command(CommandType.EXECUTE_AT, mapOf(
                    "targets" to parseSelector(context.arguments["targets"]!!.range[source]),
                    "command" to getExecuteSubcommand(context, source, server, functionManager)
                ))
            }
            "if" -> {
                when(val ifType = nodes[1].range[source]) {
                    "entity" -> {
                        Command(CommandType.EXECUTE_IF_ENTITY, mapOf(
                            "entities" to parseSelector(context.arguments["entities"]!!.range[source]),
                            "command" to getExecuteSubcommand(context, source, server, functionManager)
                        ))
                    }
                    "score" -> {
                        val comparison = nodes[4].range[source]
                        if(comparison == "matches") {
                            val range = context.arguments["range"]!!.result as MinMaxBounds.Ints
                            Command(CommandType.EXECUTE_IF_SCORE_RANGE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "range" to IntRangeArgument(range.min.orElse(null), range.max.orElse(null)),
                                "command" to getExecuteSubcommand(context, source, server, functionManager)
                            ))
                        }else {
                            Command(CommandType.EXECUTE_IF_SCORE_COMPARE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "comparison" to comparison,
                                "source" to parseScoreHolder(context.arguments["source"]!!.range[source]),
                                "sourceObjective" to context.arguments["sourceObjective"]!!.result as String,
                                "command" to getExecuteSubcommand(context, source, server, functionManager)
                            ))
                        }
                    }
                    else -> throw UnsupportedOperationException("Invalid execute command if comparison type: $ifType")
                }
            }
            "run" -> parseCommand(context.child.nodes[0].range[source], context.child, source, server, functionManager)
            "positioned" -> {
                val posArg = context.arguments["pos"]
                return if(posArg != null) {
                    when(val pos = posArg.result) {
                        is LocalCoordinates -> throw UnsupportedOperationException("Unsupported local coordinates")
                        is net.minecraft.commands.arguments.coordinates.WorldCoordinates -> {
                            Command(CommandType.EXECUTE_POSITIONED_POS, mapOf("position" to WorldCoordinates(pos.x.value, pos.x.relative, pos.y.value, pos.y.relative, pos.z.value, pos.z.relative), "command" to getExecuteSubcommand(context, source, server, functionManager)))
                        }
                        else -> throw UnsupportedOperationException("Unknown coordinate type")
                    }
                }else {
                    throw UnsupportedOperationException("Invalid execute command: positioned")
                }
            }
            "store" -> {
                val storeType = nodes[1].range[source]
                val storeLocation = nodes[2].range[source]
                if(storeType == "result") {
                    if(storeLocation == "score") {
                        return Command(CommandType.EXECUTE_STORE_RESULT_SCORE, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "command" to getExecuteSubcommand(context, source, server, functionManager)
                        ))
                    }
                }
                throw UnsupportedOperationException("Invalid execute command: store / $storeType / $storeLocation")
            }
            "unless" -> {
                when(val ifType = nodes[1].range[source]) {
                    "entity" -> {
                        Command(CommandType.EXECUTE_UNLESS_ENTITY, mapOf(
                            "entities" to parseSelector(context.arguments["entities"]!!.range[source]),
                            "command" to getExecuteSubcommand(context, source, server, functionManager)
                        ))
                    }
                    "score" -> {
                        val comparison = nodes[4].range[source]
                        if(comparison == "matches") {
                            val range = context.arguments["range"]!!.result as MinMaxBounds.Ints
                            Command(CommandType.EXECUTE_UNLESS_SCORE_RANGE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "range" to IntRangeArgument(range.min.orElse(null), range.max.orElse(null)),
                                "command" to getExecuteSubcommand(context, source, server, functionManager)
                            ))
                        }else {
                            Command(CommandType.EXECUTE_UNLESS_SCORE_COMPARE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "comparison" to comparison,
                                "source" to parseScoreHolder(context.arguments["source"]!!.range[source]),
                                "sourceObjective" to context.arguments["sourceObjective"]!!.result as String,
                                "command" to getExecuteSubcommand(context, source, server, functionManager)
                            ))
                        }
                    }

                    else -> throw UnsupportedOperationException("Invalid execute command unless comparison type: $ifType")
                }
            }
            else -> throw UnsupportedOperationException("Invalid execute command type: $type")
        }
    }

    private fun parseCommandFunction(context: CommandContextBuilder<CommandSourceStack>, source: String, functionManager: ServerFunctionManager): Command {
        if(context.nodes.size > 2) {
            throw UnsupportedOperationException("Too many function arguments, not supported")
        }
        val name = context.arguments["name"]!!.range[source]
        return if(name.startsWith("#")) {
            Command(CommandType.FUNCTION, mapOf("functions" to functionManager.getTag(ResourceLocation(name.substring(1))).toList().map { it.id.toDataLocation() }))
        }else {
            if(functionManager.library.functions.containsKey(ResourceLocation(name))) {
                Command(CommandType.FUNCTION, mapOf("functions" to listOf(ResourceLocation(name).toDataLocation())))
            }else {
                Command(CommandType.FUNCTION, mapOf("functions" to listOf<DataLocation>()))
            }
        }
    }

    private fun parseCommandGamemode(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val gamemode = (context.arguments["gamemode"]!!.result as GameType).toGamemode()
        val targets = context.arguments["target"]
        return if(targets != null) {
            Command(CommandType.GAMEMODE, mapOf("gamemode" to gamemode, "targets" to parseSelector(targets.range[source])))
        }else {
            Command(CommandType.GAMEMODE, mapOf("gamemode" to gamemode, "targets" to SelectorArgument.ofSelf()))
        }
    }

    private fun parseCommandKill(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val targets = context.arguments["targets"]
        return if(targets != null) {
            Command(CommandType.KILL, mapOf("targets" to parseSelector(targets.range[source])))
        }else {
            Command(CommandType.KILL, mapOf("targets" to SelectorArgument.ofSelf()))
        }
    }

    private fun parseCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        return when(val type = context.nodes[1].node.name) {
            "players" -> {
                when(val opType = context.nodes[2].node.name) {
                    "add" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_ADD, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "score" to context.arguments["score"]!!.result as Int
                        ))
                    }
                    "get" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_GET, mapOf(
                            "target" to parseScoreHolder(context.arguments["target"]!!.range[source]).single(),
                            "objective" to context.arguments["objective"]!!.result as String
                        ))
                    }
                    "operation" -> {
                        val operation = context.arguments["operation"]!!.range[source] as String
                        val targetObjective = context.arguments["targetObjective"]!!.result as String
                        val sourceObjective = context.arguments["sourceObjective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_OPERATION, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "targetObjective" to targetObjective,
                            "operation" to operation,
                            "source" to parseScoreHolder(context.arguments["source"]!!.range[source]),
                            "sourceObjective" to sourceObjective
                        ))
                    }
                    "remove" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_REMOVE, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ))
                    }
                    "reset" -> {
                        if(context.arguments["objective"] != null) {
                            val objective = context.arguments["objective"]!!.result as String
                            Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                                "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                                "objective" to objective
                            ))
                        }else {
                            Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                                "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source])
                            ))
                        }
                    }
                    "set" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_SET, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ))
                    }
                    else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: $opType")
                }
            }
            else -> throw UnsupportedOperationException("Invalid scoreboard command type: $type")
        }
    }

    private fun parseCommandTag(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val targets = parseSelector(context.arguments["targets"]!!.range[source])
        return when(val type = context.nodes[2].node.name) {
            "add" -> {
                Command(CommandType.TAG_ADD, mapOf(
                    "targets" to targets,
                    "name" to context.arguments["name"]!!.result as String
                ))
            }
            "remove" -> {
                Command(CommandType.TAG_REMOVE, mapOf(
                    "targets" to targets,
                    "name" to context.arguments["name"]!!.result as String
                ))
            }
            else -> throw UnsupportedOperationException("Invalid tag command type: $type")
        }
    }

    private fun getExecuteSubcommand(context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer, functionManager: ServerFunctionManager): Command? {
        return if(context.child != null) parseCommandExecute(context.child, context.child.nodes, source, server, functionManager) else null
    }

}
