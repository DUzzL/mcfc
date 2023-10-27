package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.context.CommandContextBuilder
import dev.epicpix.minecraftfunctioncompiler.arguments.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.arguments.SelectorArgument
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import java.util.*
import kotlin.UnsupportedOperationException

object CommandParser {

    fun parseFunctionCommands(func: CommandFunction): List<CommandLike> {
        val commands = mutableListOf<CommandLike>()
        for (entry in func.entries) {
            if (entry !is CommandFunction.CommandEntry) continue

            val context = entry.parse.context
            val cmdName = context.nodes[0].node.name

            commands.add(parseCommand(cmdName, context, entry.parse.reader.string))
        }
        return commands
    }

    private fun parseCommand(cmdName: String, context: CommandContextBuilder<CommandSourceStack>, source: String): CommandLike {
        return when(cmdName) {
            "execute" -> parseCommandExecute(context, context.nodes[1].range[source], source)
            "kill" -> parseCommandKill(context, source)
            "scoreboard" -> parseCommandScoreboard(context, source)
            "tag" -> parseCommandTag(context, source)
            else -> throw UnsupportedOperationException("Cannot parse command: $cmdName")
        }
    }

    private fun parseCommandExecute(context: CommandContextBuilder<CommandSourceStack>, type: String, source: String): CommandLike {
        return when(type) {
            "as" -> {
                Command(CommandType.EXECUTE_AS, mapOf(
                    "targets" to parseArgumentSelector(context.arguments["targets"]!!.range[source]),
                    "command" to parseCommandExecute(context.child, context.child.nodes[0].range[source], source)
                ), mutableListOf())
            }
            "run" -> parseCommand(context.child.nodes[0].range[source], context.child, source)
            else -> throw UnsupportedOperationException("Invalid execute command type: $type")
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
                        ), mutableListOf()).withRequirements(
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to objective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to objective)),
                        )
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
                        ), mutableListOf()).withRequirements(
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to targetObjective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to targetObjective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to sourceObjective)),
                        )
                    }
                    "remove" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_REMOVE, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf()).withRequirements(
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to objective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to objective)),
                        )
                    }
                    "reset" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf()).withRequirements(
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to objective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to objective)),
                        )
                    }
                    "set" -> {
                        val objective = context.arguments["objective"]!!.result as String
                        Command(CommandType.SCOREBOARD_PLAYERS_SET, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to objective,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf()).withRequirements(
                            CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to objective)),
                            CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to objective)),
                        )
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
        if(selector == "@s") {
            return SelectorArgument(self = true)
        }
        throw UnsupportedOperationException("Cannot parse selector: $selector")
    }

}