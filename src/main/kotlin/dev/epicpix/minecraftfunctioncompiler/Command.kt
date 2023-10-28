package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any?>?, val sideEffects: MutableList<SideEffect>) : CommandLike {

    operator fun <T> get(param: String): T {
        return params?.get(param) as T
    }

    fun withRequirements(vararg requirements: CommandRequirement): CommandBlock {
        return CommandBlock(
            listOf(this),
            requirements.distinct()
        )
    }

}

data class CommandBlock(val commands: List<CommandLike>, val requirements: List<CommandRequirement>) : CommandLike

interface CommandLike

data class CommandRequirement(val type: CommandRequirementType, val data: Map<String, Any>)


enum class CommandType {

    UNKNOWN,
    EXECUTE_AS,
    EXECUTE_IF_ENTITY,
    EXECUTE_IF_SCORE_COMPARE,
    EXECUTE_IF_SCORE_RANGE,
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
