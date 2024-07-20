package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

public record TextStyle(Integer color, Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough, Boolean obfuscated, TextClickEvent clickEvent, TextHoverEvent<?> hoverEvent, String insertion, DataLocation font) {}
