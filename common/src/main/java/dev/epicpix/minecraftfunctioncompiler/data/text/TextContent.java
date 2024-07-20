package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;

import java.util.List;

public interface TextContent {

    record LiteralText(String value) implements TextContent {}
    record TranslatableText(String key, String fallback, List<Object> args) implements TextContent { }
    record KeybindText(String keybind) implements TextContent {}
    record ScoreText(String rawSelector, SelectorArgument selector, String objective) implements TextContent {}

}
