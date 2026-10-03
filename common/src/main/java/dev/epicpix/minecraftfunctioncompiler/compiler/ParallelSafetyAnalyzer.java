package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersAdd;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersSet;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersRemove;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreAccessorArgument;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which datapack functions are safe to run in a parallel fan-out
 * ({@code execute as <multi-selector> [at @s] run function <fn>}): one fan-out element
 * per matched entity, run concurrently, mutations buffered and applied serially after.
 *
 * <p>A function is safe iff EVERY command (recursively through {@code execute} modifiers)
 * is either a pure read or a mutation that is (a) buffered by the runtime AND (b) targets
 * a PER-ELEMENT-DISTINCT holder so concurrent elements can't conflict. Currently that is
 * ONLY {@code scoreboard players set|add|remove} on {@code @s} (each fan-out entity is its
 * own score holder → no shared state; the write goes through buffer-aware
 * {@code ExtensionFunctions.setScore}). NOTE: add/remove are read-modify-write; on a SHARED holder that
 * would lose updates under parallelism, hence the {@code @s}-only restriction.
 *
 * <p>DEFAULT UNSAFE. Anything else — tags, advancements, summon, data, store, nested
 * {@code function} calls, non-{@code @s} scoreboard targets, unknown commands — makes the
 * whole function unsafe. Wrong direction = corrupted survival data, so err hard toward
 * unsafe. To widen later: make the relevant mutation helper buffer-aware AND prove its
 * target is per-element-distinct, then admit it here. See PARALLEL_DATAPACK_DESIGN.md.
 */
public final class ParallelSafetyAnalyzer {
    private ParallelSafetyAnalyzer() {}

    private static volatile Set<DataLocation> SAFE = Set.of();

    /** Recompute the safe set from all parsed functions (once per datapack (re)load). */
    public static void recompute(Map<DataLocation, List<CommandParams>> functions) {
        Set<DataLocation> safe = new HashSet<>();
        for (Map.Entry<DataLocation, List<CommandParams>> e : functions.entrySet()) {
            boolean ok = true;
            for (CommandParams c : e.getValue()) {
                if (!commandSafe(c)) { ok = false; break; }
            }
            if (ok) safe.add(e.getKey());
        }
        SAFE = safe;
    }

    public static boolean isSafe(DataLocation function) {
        return SAFE.contains(function);
    }

    private static boolean commandSafe(CommandParams c) {
        if (c == null) return true;
        // buffered, per-@s scoreboard mutations (distinct holder per fan-out element)
        if (c instanceof CommandScoreboardPlayersSet s) return holderIsSelf(s.targets());
        if (c instanceof CommandScoreboardPlayersAdd a) return holderIsSelf(a.targets());
        if (c instanceof CommandScoreboardPlayersRemove r) return holderIsSelf(r.targets());

        String n = c.getClass().getSimpleName();
        // execute modifiers (as/at/if/unless/positioned/rotated/run/...) are reads that wrap
        // a body — recurse. EXCEPT store* (writes a result), summon (spawns), and any
        // if/unless-function (runs a function as the condition → could mutate).
        if (n.startsWith("CommandExecute")) {
            if (n.startsWith("CommandExecuteStore")) return false;
            if (n.equals("CommandExecuteSummon")) return false;
            if (n.contains("Function")) return false;
            boolean wrappedBody = false;
            for (RecordComponent rc : c.getClass().getRecordComponents()) {
                if (rc.getType() == CommandParams.class) {
                    try {
                        CommandParams body = (CommandParams) rc.getAccessor().invoke(c);
                        if (!commandSafe(body)) return false;
                        wrappedBody = true;
                    } catch (ReflectiveOperationException ex) {
                        return false;
                    }
                }
            }
            return wrappedBody;
        }
        return false; // default-unsafe: any mutation / unknown command
    }

    private static boolean holderIsSelf(ScoreAccessorArgument arg) {
        var h = arg.scoreHolder();
        return h != null && !h.all() && h.name() == null && h.selector() != null && h.selector().self();
    }
}
