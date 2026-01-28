package dev.epicpix.minecraftfunctioncompiler.data;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record AdvancementData(DataLocation location, @Nullable DataLocation parent, List<String> criteria) {}
