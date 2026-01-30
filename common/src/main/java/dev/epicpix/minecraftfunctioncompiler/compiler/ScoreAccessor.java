package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;

public record ScoreAccessor(LocationData<TObjective> objective, LocationData<String> scoreHolder) {}
