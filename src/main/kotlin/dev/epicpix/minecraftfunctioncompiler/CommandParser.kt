package dev.epicpix.minecraftfunctioncompiler

import com.mojang.brigadier.context.CommandContextBuilder
import dev.epicpix.minecraftfunctioncompiler.arguments.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.arguments.SelectorArgument
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import java.lang.UnsupportedOperationException
import java.util.*

object CommandParser {

    fun parseFunctionCommands(func: CommandFunction): List<Command> {
        val commands = mutableListOf<Command>()
        for (entry in func.entries) {
            if (entry !is CommandFunction.CommandEntry) continue

            val context = entry.parse.context
            val cmdName = context.nodes[0].node.name

            commands.add(parseCommand(cmdName, context, entry.parse.reader.string))
        }
        return commands
    }

    private fun parseCommand(cmdName: String, context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        return when(cmdName) {
            "kill" -> parseCommandKill(context, source)
            "scoreboard" -> parseCommandScoreboard(context, source)
            else -> throw UnsupportedOperationException("Cannot parse command: $cmdName")
        }
    }

    private fun parseCommandKill(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val targets = context.arguments["targets"]
        return if(targets != null) {
            Command(CommandType.KILL_SELECTOR, mapOf("targets" to parseArgumentSelector(targets.range[source])), mutableListOf())
        }else {
            Command(CommandType.KILL_SELF, null, mutableListOf())
        }
    }

    private fun parseCommandScoreboard(context: CommandContextBuilder<CommandSourceStack>, source: String): Command {
        val type = context.nodes[1].node.name
        return when(type) {
            "players" -> {
                val opType = context.nodes[2].node.name
                when(opType) {
                    "add" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_ADD, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    "remove" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_REMOVE, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    "reset" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_RESET, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    "set" -> {
                        Command(CommandType.SCOREBOARD_PLAYERS_SET, mapOf(
                            "targets" to parseArgumentScoreHolder(context.arguments["targets"]!!.range[source]),
                            "objective" to context.arguments["objective"]!!.result as String,
                            "score" to context.arguments["score"]!!.result as Int
                        ), mutableListOf())
                    }
                    else -> throw UnsupportedOperationException("Unsupported scoreboard players type arguments: $opType")
                }
            }
            else -> throw UnsupportedOperationException("Invalid scoreboard command type: $type")
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
        val entityUuid = runCatching { UUID.fromString(selector) }
        if(entityUuid.isSuccess) {
            return SelectorArgument(entityUuid.getOrThrow())
        }
        throw UnsupportedOperationException("Cannot parse selector: $selector")
    }

}