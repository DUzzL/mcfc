package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.data.locations.Objective;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

public record ScoreAccessor(LocationData<Objective> objective, LocationData<String> scoreHolder) {}
