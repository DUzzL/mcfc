package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.FunctionResolver;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Assigns each compiled function to a batch-container class so that the total
 * number of JVM classes produced scales with bytecode size, not with function
 * count. This eliminates the metaspace blow-up that occurred under the legacy
 * "1 class per .mcfunction" model on large datapacks (5000+ functions).
 *
 * Granularity: a single function (including all its chunk methods and any
 * macro `runInstanced` variant) is always placed in the same batch class.
 * Cross-function invokestatic and chunk dispatch both resolve through
 * {@link FunctionResolver} so the batch class is opaque to call sites.
 *
 * Plan is deterministic: functions are sorted by DataLocation string form and
 * packed greedily into batches while per-batch budgets are respected.
 *
 * Budgets (conservative — well below JVM hard limits to leave headroom for CP
 * entries that cannot be cheaply estimated up-front):
 *   - methods per class:    {@link #MAX_METHODS_PER_BATCH}
 *   - est. bytecode bytes:  {@link #MAX_BYTES_PER_BATCH}
 *
 * If even a single function exceeds a per-batch budget on its own it still
 * gets its own batch (no splitting of a function across batches — chunk
 * dispatch assumes same-class invokestatic).
 */
public final class BatchClassPlanner implements FunctionResolver {

    /** Methods per batch. JVM limit is 65535 — we stay well below so the pipeline
     *  produces many batches for medium/large datapacks. This is deliberately small
     *  so batch-level parallelism can saturate modern CPUs during assembly; the
     *  metaspace win comes from being O(tens of classes), not from maximising
     *  methods-per-class. */
    static final int MAX_METHODS_PER_BATCH = 800;

    /** Soft target for total estimated bytecode per batch class. Kept low so we
     *  don't build a single megabatch holding all IL in memory at once. */
    static final int MAX_BYTES_PER_BATCH = 300 * 1024;

    /** Threshold from FunctionCodeGenerator; duplicated here to avoid cyclic dep. */
    private static final int MAX_INSTRUCTIONS_PER_METHOD = 4000;

    /** Rough upper-bound bytes-per-IL-instruction for budget estimation. */
    private static final int EST_BYTES_PER_INSTRUCTION = 20;

    /** Per-method fixed overhead (method_info header + small const pool growth). */
    private static final int EST_METHOD_OVERHEAD = 128;

    public static final String BATCH_CLASS_PREFIX = "mcfc/batch/Batch$";

    private final List<ClassDesc> batchClasses;
    private final Map<DataLocation, PlacedFunction> placements;

    private BatchClassPlanner(List<ClassDesc> batchClasses, Map<DataLocation, PlacedFunction> placements) {
        this.batchClasses = List.copyOf(batchClasses);
        this.placements = Map.copyOf(placements);
    }

    /**
     * Plan batch assignments for every successfully-optimized function.
     *
     * @param optimizedFunctions post-optimizer IL per function — only these are considered "compiled"
     * @param macroFunctions     subset of keys that additionally produce a runInstanced method
     */
    public static BatchClassPlanner plan(
        Map<DataLocation, List<Instruction>> optimizedFunctions,
        Set<DataLocation> macroFunctions
    ) {
        // Deterministic order so the same input always produces the same batch layout
        // (required for cache stability).
        var ordered = new ArrayList<>(optimizedFunctions.keySet());
        ordered.sort((a, b) -> a.toString().compareTo(b.toString()));

        var batchClasses = new ArrayList<ClassDesc>();
        var placements = new HashMap<DataLocation, PlacedFunction>(optimizedFunctions.size());

        int currentBatch = -1;
        int currentMethods = 0;
        int currentBytes = 0;

        for(var loc : ordered) {
            var instructions = optimizedFunctions.get(loc);
            boolean hasMacros = macroFunctions.contains(loc);
            int methods = estimateMethods(instructions.size(), hasMacros);
            int bytes = estimateBytes(instructions.size(), methods);

            boolean rollOver =
                currentBatch < 0 ||
                currentMethods + methods > MAX_METHODS_PER_BATCH ||
                currentBytes + bytes > MAX_BYTES_PER_BATCH;

            if(rollOver) {
                currentBatch = batchClasses.size();
                batchClasses.add(ClassDesc.ofInternalName(BATCH_CLASS_PREFIX + currentBatch));
                currentMethods = 0;
                currentBytes = 0;
            }

            String base = mangle(loc);
            placements.put(loc, new PlacedFunction(currentBatch, base, hasMacros));
            currentMethods += methods;
            currentBytes += bytes;
        }

        return new BatchClassPlanner(batchClasses, placements);
    }

    private static int estimateMethods(int instructionCount, boolean hasMacros) {
        int chunks = instructionCount <= MAX_INSTRUCTIONS_PER_METHOD
            ? 1
            : (instructionCount + MAX_INSTRUCTIONS_PER_METHOD - 1) / MAX_INSTRUCTIONS_PER_METHOD;
        // chunks > 1 adds a dispatch `run` on top of the chunk methods
        int runMethods = chunks == 1 ? 1 : chunks + 1;
        return hasMacros ? runMethods + 1 : runMethods;
    }

    private static int estimateBytes(int instructionCount, int methods) {
        return methods * EST_METHOD_OVERHEAD + instructionCount * EST_BYTES_PER_INSTRUCTION;
    }

    /**
     * Mangle a DataLocation into a JVM-legal method-name base. Must be unique
     * per (namespace, path) pair within the compilation — a short CRC32 of
     * the original string is appended to disambiguate sanitisation collisions
     * (e.g. "foo-bar" and "foo_bar" would otherwise both become "foo_bar").
     */
    static String mangle(DataLocation loc) {
        String raw = loc.namespace() + ":" + loc.path();
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append("fn_");
        for(int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            sb.append(safe ? c : '_');
        }
        sb.append('_');
        var crc = new CRC32();
        crc.update(raw.getBytes(StandardCharsets.UTF_8));
        sb.append(HexFormat.of().toHexDigits((int) crc.getValue()));
        return sb.toString();
    }

    /** Method name convention for chunk N of a given function's `run`. Must match
     * the chunk-name scheme in {@code FunctionCodeGenerator.emitSplitFunction}. */
    public static String chunkMethodName(String base, int chunkIndex) {
        return base + "$chunk" + chunkIndex;
    }

    /** Method name convention for a macro function's instanced body. */
    public static String instancedMethodName(String base) {
        return base + "__instanced";
    }

    public List<ClassDesc> batchClasses() {
        return batchClasses;
    }

    public PlacedFunction placement(DataLocation function) {
        return placements.get(function);
    }

    // ── FunctionResolver ─────────────────────────────────────────────────

    @Override public boolean isCompiled(DataLocation function) {
        return placements.containsKey(function);
    }

    @Override public @Nullable ResolvedTarget resolveRun(DataLocation function) {
        var p = placements.get(function);
        if(p == null) return null;
        return new ResolvedTarget(batchClasses.get(p.batchIndex()), p.baseName());
    }

    @Override public @Nullable ResolvedTarget resolveRunInstanced(DataLocation function) {
        var p = placements.get(function);
        if(p == null || !p.hasMacros()) return null;
        return new ResolvedTarget(batchClasses.get(p.batchIndex()), instancedMethodName(p.baseName()));
    }

    /** Where a function sits: which batch class, what base method name, whether it has an instanced variant. */
    public record PlacedFunction(int batchIndex, String baseName, boolean hasMacros) {}
}
