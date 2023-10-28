package dev.epicpix.minecraftfunctioncompiler

import org.objectweb.asm.Opcodes

data class GeneratorCommandContext(
    val entityLocal: String?,
    val preconditions: List<Precondition>,
) {
    fun getEntity(cw: CodeWriter) {
        cw.loadLocal(entityLocal!!)
    }

    fun withPrecondition(precondition: Precondition): GeneratorCommandContext {
        return copy(preconditions = preconditions.toMutableList().apply { add(precondition) })
    }

    fun runWithObjective(cw: CodeWriter, objective: String, run: (objective: String, context: GeneratorCommandContext) -> Unit) {
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
            cw.visitGetScoreboard()
            cw.writeString(objective)
            cw.callMethod("net/minecraft/world/scores/Scoreboard", "getObjective", "(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")
            cw.storeLocal(objName, "Lnet/minecraft/world/scores/Objective;") {
                cw.loadLocal(objName)
                cw.executeIf(Opcodes.IFNONNULL) {
                    run(objName, withPrecondition(Precondition(PreconditionType.OBJECTIVE_EXISTS, mapOf("objective" to objective))))
                }
            }
        }
    }

}

data class Precondition(val type: PreconditionType, val data: Map<String, Any>)

enum class PreconditionType {
    OBJECTIVE_EXISTS,
    OBJECTIVE_NOT_LOCKED,
    ENTITY_SELF_EXISTS
}