package dev.epicpix.minecraftfunctioncompiler.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface SelectorReader {
    void accept(StringReader reader, SelectorGeneratorBuilderData data) throws CommandSyntaxException;
}
