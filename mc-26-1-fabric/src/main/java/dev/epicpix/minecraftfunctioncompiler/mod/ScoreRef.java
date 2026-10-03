package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCompareOperation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardOperation;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * Link-time handle for one constant (score holder name, objective name) pair, kept in an array field of the
 * generated data class. It caches the {@link Objective} and the live {@link Score} of the pair; reads and writes
 * always go through the cached Score object itself, so values are never stale.
 *
 * <p>A cached Score stays valid while it is still the entry in its {@code PlayerScores} map, and a cached
 * Objective while it is registered. Entries only leave those maps through {@code PlayerScores.remove/setScore},
 * {@code Scoreboard.resetAllPlayerScores} and {@code Scoreboard.removeObjective}; the scoreboard mixins bump
 * {@link #epoch} on each, which drops every cache. Misses are not cached, so a score or objective created later
 * is found by the next lookup.
 *
 * <p>The fused command helpers (set/add/operation/matches/compare/store) each perform one whole vanilla
 * ScoreboardCommand / ExecuteCommand step with the same lookups, creations and ScoreAccess side effects
 * (see {@link ExtensionFunctions#setScore}); they return false where vanilla throws a CommandSyntaxException.
 */
public final class ScoreRef {
    /**
     * Bumped whenever a Score or an Objective may have been detached from its scoreboard. Volatile and bumped
     * under a lock: scores of entities can be reset from non-server threads (server mods with threaded entity
     * ticking), and a lost or invisible bump would leave the server thread's caches pointing at a detached Score.
     */
    public static volatile int epoch;

    public static synchronized void invalidateAll() {
        epoch++;
    }

    private static final ScoreboardOperation[] OPERATIONS = ScoreboardOperation.values();
    private static final ScoreboardCompareOperation[] COMPARISONS = ScoreboardCompareOperation.values();

    /** Element accessor of the data-class array (typed: no checkcast at the call site). */
    public static ScoreRef at(ScoreRef[] refs, int index) {
        return refs[index];
    }

    private final String holderName;
    private final String objectiveName;
    private final ScoreHolder holder;

    private @Nullable Scoreboard objectiveBoard;
    private @Nullable Objective objective;
    private int objectiveEpoch;

    private @Nullable Objective scoreObjective;
    private @Nullable Score score;
    private int scoreEpoch;

    private int lastValue;

    public ScoreRef(String holderName, String objectiveName) {
        this.holderName = holderName;
        this.objectiveName = objectiveName;
        this.holder = ScoreHolder.forNameOnly(holderName);
    }

    public ScoreHolder holder() {
        return holder;
    }

    /** The new value of the last successful set/add/operation through this ref (the command's result). */
    public int lastValue() {
        return lastValue;
    }

    /** ObjectiveArgument.getObjective: the objective, or null when it does not exist. */
    public @Nullable Objective objective(Scoreboard scoreboard) {
        Objective cached = objective;
        if(cached != null && objectiveBoard == scoreboard && objectiveEpoch == epoch) return cached;
        Objective found = scoreboard.getObjective(objectiveName);
        objectiveBoard = scoreboard;
        objective = found;
        objectiveEpoch = epoch;
        return found;
    }

    /** ObjectiveArgument.getWritableObjective: null for a missing objective or a read-only criterion. */
    private @Nullable Objective writableObjective(Scoreboard scoreboard) {
        Objective found = objective(scoreboard);
        return found == null || found.getCriteria().isReadOnly() ? null : found;
    }

    /** The live score of this holder in {@code objective}, or null (vanilla getPlayerScoreInfo; never creates). */
    public @Nullable Score find(Scoreboard scoreboard, Objective objective) {
        Score cached = score;
        if(cached != null && scoreObjective == objective && scoreEpoch == epoch) return cached;
        PlayerScores scores = scoreboard.playerScores.get(holderName);
        Score found = scores == null ? null : scores.get(objective);
        scoreObjective = objective;
        score = found;
        scoreEpoch = epoch;
        return found;
    }

    /** {@code scoreboard players set}: false when the objective is missing or read-only. */
    public boolean set(ServerScoreboard scoreboard, int value) {
        Objective target = writableObjective(scoreboard);
        if(target == null) return false;
        Score found = find(scoreboard, target);
        ExtensionFunctions.setScore(scoreboard, target, holder, ExtensionFunctions.ensureScore(scoreboard, target, holder, found), found, value);
        lastValue = value;
        return true;
    }

    /** {@code scoreboard players add|remove} (remove passes the negated amount): one access, get then set. */
    public boolean add(ServerScoreboard scoreboard, int amount) {
        Objective target = writableObjective(scoreboard);
        if(target == null) return false;
        Score found = find(scoreboard, target);
        Score live = ExtensionFunctions.ensureScore(scoreboard, target, holder, found);
        int value = live.value() + amount;
        ExtensionFunctions.setScore(scoreboard, target, holder, live, found, value);
        lastValue = value;
        return true;
    }

    /** {@code scoreboard players remove}: vanilla computes {@code get() - amount} (no negation overflow). */
    public boolean remove(ServerScoreboard scoreboard, int amount) {
        Objective target = writableObjective(scoreboard);
        if(target == null) return false;
        Score found = find(scoreboard, target);
        Score live = ExtensionFunctions.ensureScore(scoreboard, target, holder, found);
        int value = live.value() - amount;
        ExtensionFunctions.setScore(scoreboard, target, holder, live, found, value);
        lastValue = value;
        return true;
    }

    /**
     * {@code scoreboard players operation target op source} for one target and one source (ScoreboardCommand
     * .performOperation + OperationArgument): the target score is created before the source score; false when an
     * objective is missing, the target objective is read-only, or for division/modulo by zero.
     */
    public static boolean operation(ServerScoreboard scoreboard, ScoreRef target, int operation, ScoreRef source) {
        Objective targetObjective = target.writableObjective(scoreboard);
        if(targetObjective == null) return false;
        Objective sourceObjective = source.objective(scoreboard);
        if(sourceObjective == null) return false;
        Score targetFound = target.find(scoreboard, targetObjective);
        Score targetScore = ExtensionFunctions.ensureScore(scoreboard, targetObjective, target.holder, targetFound);
        Score sourceFound = source.find(scoreboard, sourceObjective);
        Score sourceScore = ExtensionFunctions.ensureScore(scoreboard, sourceObjective, source.holder, sourceFound);
        int a = targetScore.value();
        int b = sourceScore.value();
        int value;
        switch(OPERATIONS[operation]) {
            case SET -> value = b;
            case ADD -> value = a + b;
            case SUB -> value = a - b;
            case MUL -> value = a * b;
            case DIV -> {
                if(b == 0) return false;
                value = Math.floorDiv(a, b);
            }
            case MOD -> {
                if(b == 0) return false;
                value = Math.floorMod(a, b);
            }
            case MIN -> value = Math.min(a, b);
            case MAX -> value = Math.max(a, b);
            case SWAP -> {
                ExtensionFunctions.setScore(scoreboard, targetObjective, target.holder, targetScore, targetFound, b);
                ExtensionFunctions.setScore(scoreboard, sourceObjective, source.holder, sourceScore, sourceFound, a);
                target.lastValue = targetScore.value();
                return true;
            }
            default -> throw new IllegalStateException();
        }
        ExtensionFunctions.setScore(scoreboard, targetObjective, target.holder, targetScore, targetFound, value);
        target.lastValue = targetScore.value();
        return true;
    }

    /** {@code execute if score <this> matches min..max}: false for a missing objective or score. */
    public boolean matches(Scoreboard scoreboard, int min, int max) {
        Objective found = objective(scoreboard);
        if(found == null) return false;
        Score live = find(scoreboard, found);
        if(live == null) return false;
        int value = live.value();
        return value >= min && value <= max;
    }

    /** {@code execute unless score <this> matches min..max}: a missing objective fails (false), a missing score passes. */
    public boolean unlessMatches(Scoreboard scoreboard, int min, int max) {
        Objective found = objective(scoreboard);
        if(found == null) return false;
        Score live = find(scoreboard, found);
        if(live == null) return true;
        int value = live.value();
        return value < min || value > max;
    }

    /** {@code execute if score a <op> b}: false for a missing objective or score (ExecuteCommand.checkScore). */
    public static boolean compare(Scoreboard scoreboard, ScoreRef a, int comparison, ScoreRef b) {
        Objective objectiveA = a.objective(scoreboard);
        if(objectiveA == null) return false;
        Objective objectiveB = b.objective(scoreboard);
        if(objectiveB == null) return false;
        Score scoreA = a.find(scoreboard, objectiveA);
        Score scoreB = b.find(scoreboard, objectiveB);
        return scoreA != null && scoreB != null && test(COMPARISONS[comparison], scoreA.value(), scoreB.value());
    }

    /** {@code execute unless score a <op> b}: a missing objective fails (false), a missing score passes. */
    public static boolean unlessCompare(Scoreboard scoreboard, ScoreRef a, int comparison, ScoreRef b) {
        Objective objectiveA = a.objective(scoreboard);
        if(objectiveA == null) return false;
        Objective objectiveB = b.objective(scoreboard);
        if(objectiveB == null) return false;
        Score scoreA = a.find(scoreboard, objectiveA);
        Score scoreB = b.find(scoreboard, objectiveB);
        return scoreA == null || scoreB == null || !test(COMPARISONS[comparison], scoreA.value(), scoreB.value());
    }

    private static boolean test(ScoreboardCompareOperation comparison, int a, int b) {
        return switch(comparison) {
            case LT -> a < b;
            case LE -> a <= b;
            case EQ -> a == b;
            case NE -> a != b;
            case GE -> a >= b;
            case GT -> a > b;
        };
    }

    /** The live score (objective and score both present), or null: {@code scoreboard players get} fails on null. */
    public @Nullable Score score(Scoreboard scoreboard) {
        Objective found = objective(scoreboard);
        return found == null ? null : find(scoreboard, found);
    }

    /** {@code execute store … score <this> <objective>} callback: {@code objective} was resolved by the modifier. */
    public void store(ServerScoreboard scoreboard, Objective objective, int value) {
        Score found = find(scoreboard, objective);
        ExtensionFunctions.setScore(scoreboard, objective, holder, ExtensionFunctions.ensureScore(scoreboard, objective, holder, found), found, value);
    }
}
