package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class OptimizationContext {

    final LengauerTarjan tarjan;
    final ArrayList<Instruction> instructions;
    private int nextLocalId;

    // Cached edge maps — allocated once per optimization run and reused across all
    // Tarjan calls. Each call clears and refills the existing IntLinkedOpenHashSet
    // objects instead of allocating new ones, eliminating O(n) allocations per call.
    Int2ObjectOpenHashMap<IntSet> dominatorForward;
    Int2ObjectOpenHashMap<IntSet> dominatorBackward;
    Int2ObjectOpenHashMap<IntSet> deadCodeForward;

    // ── O(n) value-frequency cache ─────────────────────────────────────────────
    // Maps every Object that appears in any data[] field of any instruction to
    // the number of times it appears across ALL instructions (ALL fields, including
    // data[0] / result).  Used by OptimizationPass.isValueUsedAnywhere() to turn
    // the O(n) isValueUsed(0,n,false,v) scan into an O(1) lookup.
    // Invalidated (set to null) whenever any instruction is added, removed, or
    // replaced so it is rebuilt lazily on next access.
    @Nullable HashMap<Object, Integer> globalValueCounts = null;

    // ── O(k) type-index cache ──────────────────────────────────────────────────
    // Maps each InstructionType to the sorted list of instruction indices that
    // have that type.  Turns the O(n) scan in findInstructions() into O(k) where
    // k = number of instructions with the requested type — typically much smaller
    // than n.  Invalidated on any structural change (add / remove / replace).
    @Nullable HashMap<InstructionType, IntArrayList> typeIndex = null;

    // ── Per-(type, argIndex) value-index cache ─────────────────────────────────
    // For a given InstructionType and argument position, maps argument value to
    // the sorted list of instruction indices whose data[argIndex] equals that
    // value.  Turns single-predicate findInstructions() queries from O(k) (where
    // k = # of instructions with the requested type) to O(m) (where m = # of
    // instructions that actually match the argument value, typically 1).
    //
    // Without this index, removeDuplicateChecks on a .mcfunction with ~50k
    // IF_NULL_JUMP instructions with unique locals scans all 50k for every one
    // of them — 2.5 billion Objects.equals calls per optimization round — and
    // a single pathological config-style datapack pins a CPU core for minutes.
    //
    // Built lazily per (type, argIndex) on first query; invalidated wholesale on
    // any structural mutation.
    @Nullable HashMap<DataIndexKey, HashMap<@Nullable Object, IntArrayList>> dataIndex = null;

    record DataIndexKey(InstructionType type, int argIndex) {}

    // ── O(n) result-slot value-frequency cache ─────────────────────────────────
    // Like globalValueCounts but ONLY counts occurrences of values stored as
    // an instruction's "result" output.  Used by isUnstoredLocal() in
    // removeDeadComparisons to answer "is this local ever written by anyone?"
    // in O(1) instead of O(n).  On the user's 18 k-command waterfall pack
    // that turned a 744 s CPU loop into a constant.
    //
    // Maintained incrementally on add/remove/replace alongside
    // globalValueCounts so it never needs full rebuilding once seeded.
    @Nullable HashMap<Object, Integer> resultValueCounts = null;

    // Per-type cached "result" argument index.  -1 means the type has no
    // "result" arg (it doesn't return a value).  Computed on first need;
    // values never change since InstructionType is immutable.
    private final HashMap<InstructionType, Integer> typeResultArgIdx = new HashMap<>();

    private int resultArgIdxOf(InstructionType type) {
        Integer cached = typeResultArgIdx.get(type);
        if(cached != null) return cached;
        int idx = -1;
        String[] names = type.argumentNames();
        for(int i = 0; i < names.length; i++) {
            if("result".equals(names[i])) { idx = i; break; }
        }
        typeResultArgIdx.put(type, idx);
        return idx;
    }

    private void incResult(Instruction instr) {
        if(resultValueCounts == null) return;
        int idx = resultArgIdxOf(instr.type());
        if(idx < 0) return;
        Object v = instr.data()[idx];
        if(v == null) return;
        resultValueCounts.merge(v, 1, Integer::sum);
    }

    private void decResult(Instruction instr) {
        if(resultValueCounts == null) return;
        int idx = resultArgIdxOf(instr.type());
        if(idx < 0) return;
        Object v = instr.data()[idx];
        if(v == null) return;
        Integer c = resultValueCounts.get(v);
        if(c == null) return;
        if(c <= 1) resultValueCounts.remove(v);
        else resultValueCounts.put(v, c - 1);
    }

    OptimizationContext(ArrayList<Instruction> instructions, int nextLocalId, LengauerTarjan tarjan) {
        this.instructions = instructions;
        this.nextLocalId = nextLocalId;
        this.tarjan = tarjan;
        int n = instructions.size();
        dominatorForward  = new Int2ObjectOpenHashMap<>(n + 1);
        dominatorBackward = new Int2ObjectOpenHashMap<>(n + 1);
        deadCodeForward   = new Int2ObjectOpenHashMap<>(n + 1);
        for(int i = 0; i < n; i++) {
            dominatorForward.put(i,  new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
            dominatorBackward.put(i, new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
            deadCodeForward.put(i,   new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
        }
    }

    /** Ensure all cached edge maps contain entries 0..n-1 (grows if instructions were added). */
    void ensureCachedMapCapacity() {
        int n = instructions.size();
        while(dominatorForward.size() < n) {
            int idx = dominatorForward.size();
            dominatorForward.put(idx,  new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
            dominatorBackward.put(idx, new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
            deadCodeForward.put(idx,   new it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet(2));
        }
    }

    /**
     * Invalidates both O(n) caches.  Kept as an escape hatch for callers that
     * cannot describe their mutation incrementally.
     */
    void invalidateCaches() {
        globalValueCounts = null;
        typeIndex = null;
        dataIndex = null;
        resultValueCounts = null;
    }

    /** Increment globalValueCounts for every non-null value in {@code data}. */
    private void incCounts(@Nullable Object[] data) {
        if(globalValueCounts == null) return;
        for(var d : data) {
            if(d != null) globalValueCounts.merge(d, 1, Integer::sum);
        }
    }

    /** Decrement globalValueCounts for every non-null value in {@code data}. */
    private void decCounts(@Nullable Object[] data) {
        if(globalValueCounts == null) return;
        for(var d : data) {
            if(d == null) continue;
            Integer c = globalValueCounts.get(d);
            if(c == null) continue;
            if(c <= 1) globalValueCounts.remove(d);
            else globalValueCounts.put(d, c - 1);
        }
    }

    /**
     * Called after an instruction is inserted at {@code index}.
     * globalValueCounts is updated incrementally; typeIndex is invalidated
     * because every index >= {@code index} has shifted by +1.
     */
    void onInstructionAdded(int index, Instruction instr) {
        incCounts(instr.data());
        incResult(instr);
        typeIndex = null;
        dataIndex = null;
    }

    /**
     * Called after an instruction is removed from {@code index}.
     * globalValueCounts is updated incrementally; typeIndex is invalidated
     * because every index > {@code index} has shifted by -1.
     */
    void onInstructionRemoved(int index, Instruction removed) {
        decCounts(removed.data());
        decResult(removed);
        typeIndex = null;
        dataIndex = null;
    }

    /**
     * Called after an instruction at {@code index} is replaced in-place.
     * Both caches are updated incrementally — no index shift occurs, so the
     * typeIndex only needs the swap if the type actually changed.
     */
    void onInstructionReplaced(int index, Instruction oldInstr, Instruction newInstr) {
        if(globalValueCounts != null) {
            decCounts(oldInstr.data());
            incCounts(newInstr.data());
        }
        if(resultValueCounts != null) {
            decResult(oldInstr);
            incResult(newInstr);
        }
        if(typeIndex != null && oldInstr.type() != newInstr.type()) {
            var oldList = typeIndex.get(oldInstr.type());
            if(oldList != null) {
                int pos = it.unimi.dsi.fastutil.ints.IntArrays.binarySearch(
                    oldList.elements(), 0, oldList.size(), index);
                if(pos >= 0) oldList.removeInt(pos);
            }
            var newList = typeIndex.computeIfAbsent(newInstr.type(), k -> new IntArrayList());
            int ins = it.unimi.dsi.fastutil.ints.IntArrays.binarySearch(
                newList.elements(), 0, newList.size(), index);
            if(ins < 0) ins = -ins - 1;
            newList.add(ins, index);
        }
        // Data values and/or type may have changed; the incremental patching of
        // dataIndex across an arbitrary data rewrite is fiddly enough that full
        // invalidation is the safer default.  Rebuilding is O(n) per queried
        // (type, argIndex) on next access, still vastly cheaper than the O(k²)
        // scan it replaces.
        dataIndex = null;
    }

    /**
     * Returns (and lazily builds) a value → count-of-result-stores map.
     * Each entry is: value → number of instructions whose "result" arg slot
     * holds that value.  Use this to ask "is this local ever written?"
     * without scanning every instruction.
     */
    HashMap<Object, Integer> getResultValueCounts() {
        if(resultValueCounts == null) {
            resultValueCounts = new HashMap<>();
            for(var instr : instructions) {
                int idx = resultArgIdxOf(instr.type());
                if(idx < 0) continue;
                Object v = instr.data()[idx];
                if(v != null) resultValueCounts.merge(v, 1, Integer::sum);
            }
        }
        return resultValueCounts;
    }

    /** Public view of {@link #resultArgIdxOf} for callers in this package. */
    int resultArgIndex(InstructionType type) {
        return resultArgIdxOf(type);
    }

    /**
     * Returns (and lazily builds) the global value-frequency map.
     * Each entry is: value → number of instructions whose data[] contains that value.
     */
    HashMap<Object, Integer> getGlobalValueCounts() {
        if(globalValueCounts == null) {
            globalValueCounts = new HashMap<>();
            for(var instr : instructions) {
                for(var d : instr.data()) {
                    if(d != null) globalValueCounts.merge(d, 1, Integer::sum);
                }
            }
        }
        return globalValueCounts;
    }

    /**
     * Returns (and lazily builds) the type-to-indices index.
     * The IntArrayList for each type is in ascending index order.
     */
    HashMap<InstructionType, IntArrayList> getTypeIndex() {
        if(typeIndex == null) {
            typeIndex = new HashMap<>();
            int n = instructions.size();
            for(int i = 0; i < n; i++) {
                typeIndex.computeIfAbsent(instructions.get(i).type(), k -> new IntArrayList()).add(i);
            }
        }
        return typeIndex;
    }

    /**
     * Returns (and lazily builds) a value → indices map for the given
     * ({@code type}, {@code argIndex}) pair.  An entry {@code v → [i1, i2, ...]}
     * means instructions {@code i1, i2, ...} all have {@code type} and
     * {@code data[argIndex].equals(v)}.
     *
     * <p>Index lists are in ascending order.  Returned map may contain a
     * {@code null} key if any instruction of the given type stores
     * {@code null} at {@code argIndex}.
     */
    HashMap<@Nullable Object, IntArrayList> getDataIndex(InstructionType type, int argIndex) {
        if(dataIndex == null) dataIndex = new HashMap<>();
        var key = new DataIndexKey(type, argIndex);
        var cached = dataIndex.get(key);
        if(cached != null) return cached;
        var map = new HashMap<@Nullable Object, IntArrayList>();
        var typed = getTypeIndex().get(type);
        if(typed != null) {
            for(var it = typed.intIterator(); it.hasNext(); ) {
                int i = it.nextInt();
                Object v = instructions.get(i).data()[argIndex];
                map.computeIfAbsent(v, k -> new IntArrayList()).add(i);
            }
        }
        dataIndex.put(key, map);
        return map;
    }

    public Instruction getInstruction(int index) {
        return instructions.get(Objects.checkIndex(index, instructions.size()));
    }

    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    public int nextLocalId() {
        return nextLocalId++;
    }
}
