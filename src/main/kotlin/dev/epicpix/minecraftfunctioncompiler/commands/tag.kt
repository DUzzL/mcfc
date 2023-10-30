package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.*
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Opcodes

fun generate_tag_add(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
        getter()
        cw.writeString(command["name"])
        cw.callMethod("net/minecraft/world/entity/Entity", "addTag", "(Ljava/lang/String;)Z")
        cw.methodVisitor.visitInsn(Opcodes.POP)
    }
}

fun generate_tag_remove(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    EntitySelectorCodeGenerator.generateForEachEntity(cw, command["targets"], context) { getter ->
        getter()
        cw.writeString(command["name"])
        cw.callMethod("net/minecraft/world/entity/Entity", "removeTag", "(Ljava/lang/String;)Z")
        cw.methodVisitor.visitInsn(Opcodes.POP)
    }
}