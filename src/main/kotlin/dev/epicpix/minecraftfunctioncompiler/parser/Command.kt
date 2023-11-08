package dev.epicpix.minecraftfunctioncompiler.parser

data class Command(val type: CommandType, val params: Map<String, Any?>) {

    operator fun <T> get(param: String): T {
        return params[param] as T
    }

}


enum class CommandType {
    EXECUTE_AS,
    EXECUTE_AT,
    EXECUTE_IF_ENTITY,
    EXECUTE_IF_SCORE_COMPARE,
    EXECUTE_IF_SCORE_RANGE,
    EXECUTE_POSITIONED_POS,
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

