package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Opcodes

data class GeneratorCommandContext(
    val entityLocal: String?,
    val originalEntity: Boolean,
    val preconditions: List<Precondition>,
    val functionDependencies: List<CommandDependency>,
) {
    fun getEntity(cw: CodeWriter) {
        cw.loadLocal(entityLocal!!)
    }

    private fun withPrecondition(precondition: Precondition): GeneratorCommandContext {
        return copy(preconditions = preconditions.toMutableList().apply { add(precondition) })
    }

    fun runWithObjective(cw: CodeWriter, objective: String, run: (objective: String, context: GeneratorCommandContext) -> Unit) {
        val dependency = CommandDependencies.objective(objective)
        if(!functionDependencies.contains(dependency)) {
            throw UnsupportedOperationException("Failed to fulfill requirement of $dependency")
        }

        var doesExist = false
        for(precondition in preconditions) {
            if(precondition.type == PreconditionType.OBJECTIVE_EXISTS) {
                if(precondition.data["objective"] == objective) {
                    doesExist = true
                    break
                }
            }
        }
        val objName = "\$\$Objective:$objective"
        if(doesExist) {
            run(objName, this)
        }else {
            cw.loadLocal(objName)
            cw.executeIf(Opcodes.IFNONNULL) {
                run(objName, withPrecondition(Precondition(PreconditionType.OBJECTIVE_EXISTS, mapOf("objective" to objective))))
            }
        }
    }

}

fun createCommandContext(dependencies: List<CommandDependency>, cw: CodeWriter): GeneratorCommandContext {
    val entityUid = cw.generateUniqueLocal("CurrentEntity")
    cw.loadLocal("CommandSourceStack")
    cw.callMethod("net/minecraft/commands/CommandSourceStack", "getEntity", "()Lnet/minecraft/world/entity/Entity;")
    cw.defineGlobalLocal(entityUid, "Lnet/minecraft/world/entity/Entity;")
    cw.storeLocal(entityUid)
    for(dependency in dependencies) {
        when(dependency.type) {
            CommandDependencyType.OBJECTIVE -> {
                val objective = dependency.data["objective"] as String
                cw.visitGetScoreboard()
                cw.writeString(objective)
                cw.callMethod("net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")
                cw.defineGlobalLocal("\$\$Objective:$objective", "Lnet/minecraft/world/scores/Objective;")
                cw.storeLocal("\$\$Objective:$objective")
            }
        }
    }
    cw.visitGetPlayerList(load = false)
    return GeneratorCommandContext(entityUid, true, listOf(), dependencies)
}

data class Precondition(val type: PreconditionType, val data: Map<String, Any>)

enum class PreconditionType {
    OBJECTIVE_EXISTS,
    OBJECTIVE_NOT_LOCKED,
    ENTITY_SELF_EXISTS
}