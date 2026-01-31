package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScoreHolder;

public record ScoreAccessor(LocationData<TObjective> objective, LocationData<? extends TScoreHolder> scoreHolder) {}
