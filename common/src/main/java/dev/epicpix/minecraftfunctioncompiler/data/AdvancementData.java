package dev.epicpix.minecraftfunctioncompiler.data;

import java.util.List;

public record AdvancementData(DataLocation location, DataLocation parent, List<String> criteria) {}
