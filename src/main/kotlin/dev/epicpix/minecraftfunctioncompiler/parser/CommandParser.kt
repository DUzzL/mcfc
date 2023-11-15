package dev.epicpix.minecraftfunctioncompiler.parser

import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedCommandNode
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.coordinates.LocalCoordinates
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.level.GameType
import java.util.*

object CommandParser {

    fun parseFunctionCommands(func: CommandFunction, server: MinecraftServer): List<Command> {
        val commands = mutableListOf<Command>()
        for (entry in func.entries) {
            if (entry !is CommandFunction.CommandEntry) continue

            val context = entry.parse.context
            val cmdName = context.nodes[0].node.name

            commands.add(parseCommand(cmdName, context, entry.parse.reader.string, server))
        }
        return commands
    }

    private fun parseCommand(cmdName: String, context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer): Command {
        return when(cmdName) {
            "attribute" -> parseCommandAttribute(context, source)
            "execute" -> parseCommandExecute(context, context.nodes.subList(1, context.nodes.size), source, server)
            "function" -> parseCommandFunction(context, source, server)
            "gamemode" -> parseCommandGamemode(context, source)
            "kill" -> parseCommandKill(context, source)
            "scoreboard" -> parseCommandScoreboard(context, source)
            "tag" -> parseCommandTag(context, source)
            else -> throw UnsupportedOperationException("Cannot parse command: $cmdName")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCommandAttribute(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val target = parseSelector(context.arguments["target"]!!.range[source])
        val attributeHolder = (context.arguments["attribute"]!!.result as Holder.Reference<Attribute>)
        val runType = context.nodes[3]!!.range[source]
        if(runType == "base") {
            val accessType = context.nodes[4]!!.range[source]
            if(accessType == "set") {
                val value = context.arguments["value"]!!.result as Double
                return Command(CommandType.ATTRIBUTE_BASE_SET, mapOf(
                    "target" to target,
                    "attribute" to attributeHolder.key().location(),
                    "value" to value
                ))
            }
            throw UnsupportedOperationException("Attributes not implemented: base - $accessType ${attributeHolder.key()}")
        }
        throw UnsupportedOperationException("Attributes not implemented: $runType ${attributeHolder.key()}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCommandExecute(context: CommandContextBuilder<CommandSourceStack>, nodes: List<ParsedCommandNode<CommandSourceStack>>, source: String, server: MinecraftServer): Command {
        val type = nodes[0].range[source]
        return when(type) {
            "align" -> {
                val swizzle = context.arguments["axes"]!!.result as EnumSet<Direction.Axis>

                Command(CommandType.EXECUTE_ALIGN, mapOf(
                    "swizzle" to SwizzleArgument(swizzle.contains(Direction.Axis.X), swizzle.contains(Direction.Axis.Y), swizzle.contains(Direction.Axis.Z)),
                    "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                ))
            }
            "as" -> {
                Command(CommandType.EXECUTE_AS, mapOf(
                    "targets" to parseSelector(context.arguments["targets"]!!.range[source]),
                    "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                ))
            }
            "at" -> {
                Command(CommandType.EXECUTE_AT, mapOf(
                    "targets" to parseSelector(context.arguments["targets"]!!.range[source]),
                    "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                ))
            }
            "if" -> {
                val ifType = nodes[1].range[source]
                when(ifType) {
                    "entity" -> {
                        Command(CommandType.EXECUTE_IF_ENTITY, mapOf(
                            "entities" to parseSelector(context.arguments["entities"]!!.range[source]),
                            "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
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
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ))
                        }else {
                            Command(CommandType.EXECUTE_IF_SCORE_COMPARE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "comparison" to comparison,
                                "source" to parseScoreHolder(context.arguments["source"]!!.range[source]),
                                "sourceObjective" to context.arguments["sourceObjective"]!!.result as String,
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ))
                        }
                    }
                    else -> throw UnsupportedOperationException("Invalid execute command if comparison type: $ifType")
                }
            }
            "run" -> parseCommand(context.child.nodes[0].range[source], context.child, source, server)
            "positioned" -> {
                val posArg = context.arguments["pos"]
                return if(posArg != null) {
                    val pos = posArg.result
                    when(pos) {
                        is LocalCoordinates -> throw UnsupportedOperationException("Unsupported local coordinates")
                        is net.minecraft.commands.arguments.coordinates.WorldCoordinates -> {
                            Command(CommandType.EXECUTE_POSITIONED_POS, mapOf("position" to WorldCoordinates(pos.x.value, pos.x.relative, pos.y.value, pos.y.relative, pos.z.value, pos.z.relative), "command" to parseCommandExecute(context.child, context.child.nodes, source, server)))
                        }
                        else -> throw UnsupportedOperationException("Unknown coordinate type")
                    }
                }else {
                    throw UnsupportedOperationException("Invalid execute command: positioned")
                }
            }
            "unless" -> {
                val ifType = nodes[1].range[source]
                when(ifType) {
                    "entity" -> {
                        Command(CommandType.EXECUTE_UNLESS_ENTITY, mapOf(
                            "entities" to parseSelector(context.arguments["entities"]!!.range[source]),
                            "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
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
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ))
                        }else {
                            Command(CommandType.EXECUTE_UNLESS_SCORE_COMPARE, mapOf(
                                "target" to parseScoreHolder(context.arguments["target"]!!.range[source]),
                                "targetObjective" to context.arguments["targetObjective"]!!.result as String,
                                "comparison" to comparison,
                                "source" to parseScoreHolder(context.arguments["source"]!!.range[source]),
                                "sourceObjective" to context.arguments["sourceObjective"]!!.result as String,
                                "command" to parseCommandExecute(context.child, context.child.nodes, source, server)
                            ))
                        }
                    }

                    else -> throw UnsupportedOperationException("Invalid execute command unless comparison type: $ifType")
                }
            }
            else -> throw UnsupportedOperationException("Invalid execute command type: $type")
        }
    }

    private fun parseCommandFunction(context: CommandContextBuilder<CommandSourceStack>, source: String, server: MinecraftServer): Command {
        if(context.nodes.size > 2) {
            throw UnsupportedOperationException("Too many function arguments, not supported")
        }
        val name = context.arguments["name"]!!.range[source]
        return if(name.startsWith("#")) {
            Command(CommandType.FUNCTION, mapOf("functions" to server.functions.getTag(ResourceLocation(name.substring(1))).toList()))
        }else {
            Command(CommandType.FUNCTION, mapOf("functions" to listOf(ResourceLocation(name))))
        }
    }

    private fun parseCommandGamemode(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val gamemode = context.arguments["gamemode"]!!.result as GameType
        val targets = context.arguments["target"]
        return if(targets != null) {
            Command(CommandType.GAMEMODE, mapOf("gamemode" to gamemode, "targets" to parseSelector(targets.range[source])))
        }else {
            Command(CommandType.GAMEMODE, mapOf("gamemode" to gamemode, "targets" to SelectorArgument(self = true)))
        }
    }

    private fun parseCommandKill(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val targets = context.arguments["targets"]
        return if(targets != null) {
            Command(CommandType.KILL, mapOf("targets" to parseSelector(targets.range[source])))
        }else {
            Command(CommandType.KILL, mapOf("targets" to SelectorArgument(self = true)))
        }
    }

    private fun parseCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val type = context.nodes[1].node.name
        return when(type) {
            "players" -> {
                val opType = context.nodes[2].node.name
                when(opType) {
                    "add" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_ADD, mapOf(
                            "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
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
//                            Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
//                                "targets" to parseScoreHolder(context.arguments["targets"]!!.range[source])
//                            ))
                            throw UnsupportedOperationException("Unsupported in compiler")
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
        val type = context.nodes[2].node.name
        return when(type) {
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

}
