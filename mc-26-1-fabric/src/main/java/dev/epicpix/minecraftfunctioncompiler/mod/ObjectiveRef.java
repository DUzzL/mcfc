package dev.epicpix.minecraftfunctioncompiler.mod;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * Link-time handle for one constant objective name, stored in a static field of the generated data class.
 * Caches the {@link Objective} of the given scoreboard until {@link ScoreRef#epoch} changes (objective removal
 * bumps it). A missing objective is not cached, so an objective added later is found by the next lookup.
 */
public final class ObjectiveRef {
    private final String name;
    private @Nullable Scoreboard scoreboard;
    private @Nullable Objective objective;
    private int cachedEpoch;

    public ObjectiveRef(String name) {
        this.name = name;
    }

    /** Element accessor of the data-class array (typed: no checkcast at the call site). */
    public static ObjectiveRef at(ObjectiveRef[] refs, int index) {
        return refs[index];
    }

    public @Nullable Objective get(Scoreboard scoreboard) {
        Objective cached = objective;
        if(cached != null && this.scoreboard == scoreboard && cachedEpoch == ScoreRef.epoch) return cached;
        Objective found = scoreboard.getObjective(name);
        this.scoreboard = scoreboard;
        this.objective = found;
        this.cachedEpoch = ScoreRef.epoch;
        return found;
    }
}
