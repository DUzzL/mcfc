package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.FunctionResolver;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Assigns each function to a batch-container class so that the number of JVM classes
 * scales with bytecode size, not with function count.
 *
 * The plan is made BEFORE any function is compiled, from parsed command counts only:
 * call sites are emitted as {@code invokestatic <batch>.<base>} while other functions
 * are still compiling, so every function's (batch, method name) must be fixed up
 * front. A function that later fails to compile keeps its slot and gets a same-named
 * stub that dispatches to vanilla, so call sites never need re-emission.
 *
 * Granularity: all methods of one function (entry, chunks, dispatchers, macro body)
 * live in the same batch class. Plan is deterministic for a given input and
 * parallelism: functions are sorted by id and packed greedily.
 */
public final class BatchClassPlanner implements FunctionResolver {

    /** Methods per batch (JVM limit 65535). */
    static final int MAX_METHODS_PER_BATCH = 800;

    /** Upper bound on estimated bytecode per batch class. */
    static final int MAX_BYTES_PER_BATCH = 300 * 1024;

    /** Lower bound, so tiny packs don't produce a class per function. */
    static final int MIN_BYTES_PER_BATCH = 32 * 1024;

    /** Batches are the unit of parallel compilation: aim for this many per worker so
     *  one heavy batch doesn't leave the other workers idle. */
    static final int BATCHES_PER_WORKER = 3;

    /** Rough emitted bytes per command (measured ~60-110 on BACAP and the benchpack). */
    private static final int EST_BYTES_PER_COMMAND = 80;

    /** Per-method fixed overhead (method_info header + small const pool growth). */
    private static final int EST_METHOD_OVERHEAD = 128;

    public static final String BATCH_CLASS_PREFIX = "mcfc/batch/Batch$";

    private final List<ClassDesc> batchClasses;
    private final List<List<DataLocation>> batchMembers;
    private final Map<DataLocation, PlacedFunction> placements;

    private BatchClassPlanner(List<ClassDesc> batchClasses, List<List<DataLocation>> batchMembers, Map<DataLocation, PlacedFunction> placements) {
        this.batchClasses = List.copyOf(batchClasses);
        this.batchMembers = batchMembers;
        this.placements = Map.copyOf(placements);
    }

    /**
     * Plan batch assignments for every function that will be compiled.
     *
     * @param commandCounts parsed command count per function
     * @param parallelism   number of compile workers
     */
    public static BatchClassPlanner plan(Map<DataLocation, Integer> commandCounts, int parallelism) {
        var ordered = new ArrayList<>(commandCounts.keySet());
        ordered.sort((a, b) -> a.toString().compareTo(b.toString()));

        long totalBytes = 0;
        for(var loc : ordered) totalBytes += estimateBytes(commandCounts.get(loc));
        long budget = totalBytes / Math.max(1, (long) parallelism * BATCHES_PER_WORKER);
        int batchBudget = (int) Math.clamp(budget, MIN_BYTES_PER_BATCH, MAX_BYTES_PER_BATCH);

        var batchClasses = new ArrayList<ClassDesc>();
        var batchMembers = new ArrayList<List<DataLocation>>();
        var placements = new HashMap<DataLocation, PlacedFunction>(ordered.size());

        int currentMethods = 0;
        int currentBytes = 0;
        for(var loc : ordered) {
            int bytes = estimateBytes(commandCounts.get(loc));
            int methods = estimateMethods(bytes);

            boolean rollOver = batchClasses.isEmpty() ||
                currentMethods + methods > MAX_METHODS_PER_BATCH ||
                currentBytes + bytes > batchBudget;
            if(rollOver) {
                batchClasses.add(ClassDesc.ofInternalName(BATCH_CLASS_PREFIX + batchClasses.size()));
                batchMembers.add(new ArrayList<>());
                currentMethods = 0;
                currentBytes = 0;
            }

            int batch = batchClasses.size() - 1;
            placements.put(loc, new PlacedFunction(batch, mangle(loc), false));
            batchMembers.get(batch).add(loc);
            currentMethods += methods;
            currentBytes += bytes;
        }

        var members = new ArrayList<List<DataLocation>>(batchMembers.size());
        for(var list : batchMembers) members.add(List.copyOf(list));
        return new BatchClassPlanner(batchClasses, List.copyOf(members), placements);
    }

    private static int estimateBytes(int commandCount) {
        return EST_METHOD_OVERHEAD + commandCount * EST_BYTES_PER_COMMAND;
    }

    private static int estimateMethods(int bytes) {
        int chunks = Math.max(1, bytes / FunctionCodeGenerator.TARGET_METHOD_COST);
        // several chunks add a dispatcher (and macro functions a trampoline; rare)
        return chunks == 1 ? 1 : chunks + 1;
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

    /** Method name of command chunk {@code chunkIndex} of the method {@code owner}. */
    public static String chunkMethodName(String owner, int chunkIndex) {
        return owner + "$chunk" + chunkIndex;
    }

    /** Method name of dispatch group {@code groupIndex} of the method {@code owner}. */
    public static String groupMethodName(String owner, int groupIndex) {
        return owner + "$group" + groupIndex;
    }

    /** Method name convention for a macro function's instanced body. */
    public static String instancedMethodName(String base) {
        return base + "__instanced";
    }

    public List<ClassDesc> batchClasses() {
        return batchClasses;
    }

    /** Functions of each batch, sorted by id. */
    public List<List<DataLocation>> batchMembers() {
        return batchMembers;
    }

    public PlacedFunction placement(DataLocation function) {
        return placements.get(function);
    }

    public java.util.Set<DataLocation> functions() {
        return placements.keySet();
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

    /** Where a function sits: which batch class, what base method name, whether it has an instanced variant. */
    public record PlacedFunction(int batchIndex, String baseName, boolean hasMacros) {
        public PlacedFunction withMacros(boolean macros) {
            return macros == hasMacros ? this : new PlacedFunction(batchIndex, baseName, macros);
        }
    }
}
