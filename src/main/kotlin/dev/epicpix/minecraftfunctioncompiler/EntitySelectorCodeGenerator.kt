package dev.epicpix.minecraftfunctioncompiler

import net.minecraft.commands.arguments.selector.EntitySelector

object EntitySelectorCodeGenerator {
    fun generateForEachEntity(cw: CodeWriter, selector: EntitySelector, stringSelector: String?, run: () -> Unit) {
        throw UnsupportedOperationException("Selectors are not implemented yet")
    }
}