package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.*
import net.minecraft.resources.ResourceLocation

fun generate_kill(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
        getter()
        cw.callMethod("net/minecraft/world/entity/Entity", "kill", "()V")
    }
}