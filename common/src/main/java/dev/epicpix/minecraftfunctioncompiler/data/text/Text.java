package dev.epicpix.minecraftfunctioncompiler.data.text;

import java.util.List;

public record Text(List<Text> siblings, TextStyle style, TextContent content) {}
