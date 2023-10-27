package dev.epicpix.minecraftfunctioncompiler

data class Command(val type: CommandType, val params: Map<String, Any?>?, val sideEffects: MutableList<SideEffect>) : CommandLike {

    operator fun <T> get(param: String): T {
        return params?.get(param) as T
    }

    fun withRequirements(vararg requirements: CommandRequirement): CommandBlock {
        return CommandBlock(
            listOf(this),
            requirements.toList()
        )
    }

}

data class CommandBlock(val commands: List<CommandLike>, val requirements: List<CommandRequirement>) : CommandLike

interface CommandLike

data class CommandRequirement(val type: CommandRequirementType, val data: Map<String, Any>)

enum class CommandRequirementType {
    OBJECTIVE_EXISTS,
    OBJECTIVE_NOT_LOCKED,
    ENTITY_SELF_EXISTS
}
