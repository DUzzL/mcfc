package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScoreHolder;
import org.jspecify.annotations.Nullable;

/** A resolved score: objective local plus holder local; {@code holderName} is set when the holder is a constant
 * name, so the score lookup can use a link-time cache ({@code FIND_SCORE_REF}). */
public record ScoreAccessor(LocationData<TObjective> objective, String objectiveName, LocationData<? extends TScoreHolder> scoreHolder, @Nullable String holderName) {}
