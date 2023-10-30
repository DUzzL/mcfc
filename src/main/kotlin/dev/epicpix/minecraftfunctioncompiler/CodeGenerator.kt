package dev.epicpix.minecraftfunctioncompiler

import dev.epicpix.minecraftfunctioncompiler.commands.*
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

object CodeGenerator {

    fun generateCommandLike(commandLike: CommandLike, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        if(commandLike is Command) {
            generateCommand(commandLike, context, cw, definedFunctions)
        }else if(commandLike is CommandBlock) {
            generateCommandBlock(commandLike, context, cw, definedFunctions)
        }
    }

    fun generateCommandBlock(block: CommandBlock, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        val skipLabel = Label()
        var index = 0
        fun fullfilRequirements(context: GeneratorCommandContext) {
            if(index < block.requirements.size) {
                fulfillRequirement(block.requirements[index++], context, skipLabel, cw) { fullfilRequirements(it) }
            }else {
                for(commandLike in block.commands) {
                    generateCommandLike(commandLike, context, cw, definedFunctions)
                }
            }
        }
        fullfilRequirements(context)
        cw.methodVisitor.visitLabel(skipLabel)
    }

    fun fulfillRequirement(requirement: CommandRequirement, context: GeneratorCommandContext, skipLabel: Label, cw: CodeWriter, next: (context: GeneratorCommandContext) -> Unit) {
        when(requirement.type) {
            CommandRequirementType.OBJECTIVE_EXISTS -> {
                context.runWithObjective(cw, requirement.data["objective"] as String) { _, ctx -> next(ctx) }
            }
            CommandRequirementType.OBJECTIVE_NOT_LOCKED -> {
                context.runWithObjective(cw, requirement.data["objective"] as String) { obj, ctx ->
                    cw.loadLocal(obj)
                    cw.callMethod("net/minecraft/world/scores/Objective", "getCriteria", "()Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;")
                    cw.callMethod("net/minecraft/world/scores/criteria/ObjectiveCriteria", "isReadOnly", "()Z")
                    cw.executeIf(Opcodes.IFNE) {
                        next(ctx)
                    }
                }
            }
            CommandRequirementType.ENTITY_SELF_EXISTS -> {
                context.getEntity(cw)
                cw.methodVisitor.visitJumpInsn(Opcodes.IFNULL, skipLabel)
            }
        }
    }

    fun generateCommand(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
        when(command.type) {
            CommandType.EXECUTE_AS -> generate_execute_as(command, context, cw, definedFunctions)
            CommandType.EXECUTE_IF_ENTITY -> generate_execute_if_entity(command, context, cw, definedFunctions)
            CommandType.EXECUTE_IF_SCORE_COMPARE -> generate_execute_if_score_compare(command, context, cw, definedFunctions)
            CommandType.EXECUTE_IF_SCORE_RANGE -> generate_execute_if_score_range(command, context, cw, definedFunctions)
            CommandType.EXECUTE_UNLESS_ENTITY -> generate_execute_unless_entity(command, context, cw, definedFunctions)
            CommandType.EXECUTE_UNLESS_SCORE_COMPARE -> generate_execute_unless_score_compare(command, context, cw, definedFunctions)
            CommandType.EXECUTE_UNLESS_SCORE_RANGE -> generate_execute_unless_score_range(command, context, cw, definedFunctions)
            CommandType.FUNCTION -> generate_function(command, context, cw, definedFunctions)
            CommandType.KILL -> generate_kill(command, context, cw, definedFunctions)
            CommandType.SCOREBOARD_PLAYERS_ADD -> generate_scoreboard_players_add(command, context, cw, definedFunctions)
            CommandType.SCOREBOARD_PLAYERS_OPERATION -> generate_scoreboard_players_operation(command, context, cw, definedFunctions)
            CommandType.SCOREBOARD_PLAYERS_REMOVE -> generate_scoreboard_players_remove(command, context, cw, definedFunctions)
            CommandType.SCOREBOARD_PLAYERS_RESET -> generate_scoreboard_players_reset(command, context, cw, definedFunctions)
            CommandType.SCOREBOARD_PLAYERS_SET -> generate_scoreboard_players_set(command, context, cw, definedFunctions)
            CommandType.TAG_ADD -> generate_tag_add(command, context, cw, definedFunctions)
            CommandType.TAG_REMOVE -> generate_tag_remove(command, context, cw, definedFunctions)
        }
    }

}
