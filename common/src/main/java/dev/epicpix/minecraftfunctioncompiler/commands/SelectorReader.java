package dev.epicpix.minecraftfunctioncompiler.commands;

@FunctionalInterface
public interface SelectorReader {
    void accept(StringCommandReader reader, SelectorGeneratorBuilderData data);
}
