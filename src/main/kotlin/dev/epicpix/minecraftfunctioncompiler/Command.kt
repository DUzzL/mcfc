package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any?>, val dependencies: List<CommandDependency> = listOf()) : CommandLike {

    operator fun <T> get(param: String): T {
        return params[param] as T
    }

    fun withDependencies(vararg dependencies: CommandDependency): Command {
        return copy(dependencies = dependencies.toList())
    }

    fun toCommandBlock(): CommandBlock {
        val requirements: MutableList<CommandRequirement> = mutableListOf()
        when(type) {
            CommandType.EXECUTE_IF_SCORE_RANGE -> {
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("targetObjective"))))
            }
            CommandType.EXECUTE_IF_SCORE_COMPARE -> {
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("targetObjective"))))
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("sourceObjective"))))
            }
            CommandType.SCOREBOARD_PLAYERS_ADD, CommandType.SCOREBOARD_PLAYERS_REMOVE, CommandType.SCOREBOARD_PLAYERS_RESET, CommandType.SCOREBOARD_PLAYERS_SET -> {
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("objective"))))
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to get("objective"))))
            }
            CommandType.SCOREBOARD_PLAYERS_OPERATION -> {
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("targetObjective"))))
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_NOT_LOCKED, mapOf("objective" to get("targetObjective"))))
                requirements.add(CommandRequirement(CommandRequirementType.OBJECTIVE_EXISTS, mapOf("objective" to get("sourceObjective"))))
            }
            else -> {}
        }
        return CommandBlock(
            listOf(this),
            requirements.distinct()
        )
    }

}

data class CommandBlock(val commands: List<CommandLike>, val requirements: List<CommandRequirement>) : CommandLike

interface CommandLike

data class CommandRequirement(val type: CommandRequirementType, val data: Map<String, Any>)
data class CommandDependency(val type: CommandDependencyType, val data: Map<String, Any>)

object CommandDependencies {

    fun objective(objective: String): CommandDependency = CommandDependency(CommandDependencyType.OBJECTIVE, mapOf("objective" to objective))

}


enum class CommandType {
    EXECUTE_AS,
    EXECUTE_IF_ENTITY,
    EXECUTE_IF_SCORE_COMPARE,
    EXECUTE_IF_SCORE_RANGE,
    EXECUTE_UNLESS_ENTITY,
    EXECUTE_UNLESS_SCORE_COMPARE,
    EXECUTE_UNLESS_SCORE_RANGE,
    FUNCTION,
    KILL,
    SCOREBOARD_PLAYERS_ADD,
    SCOREBOARD_PLAYERS_OPERATION,
    SCOREBOARD_PLAYERS_REMOVE,
    SCOREBOARD_PLAYERS_RESET,
    SCOREBOARD_PLAYERS_SET,
    TAG_ADD,
    TAG_REMOVE,
}

enum class CommandRequirementType {
    OBJECTIVE_EXISTS,
    OBJECTIVE_NOT_LOCKED,
    ENTITY_SELF_EXISTS
}


enum class CommandDependencyType {
    OBJECTIVE,
}
