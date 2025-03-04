package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;

import java.util.List;

public interface TextContent {

    record LiteralText(String value) implements TextContent {}
    record TranslatableText(String key, String fallback, List<Object> args) implements TextContent { }
    record KeybindText(String keybind) implements TextContent {}
    record ScoreText(ScoreHolderArgument scoreHolder, String objective) implements TextContent {}
    record SelectorText(SelectorArgument selector, Text separator) implements TextContent {}
    record NbtText(NbtPath path, Text separator, NbtDataLocation source) implements TextContent {}

}
