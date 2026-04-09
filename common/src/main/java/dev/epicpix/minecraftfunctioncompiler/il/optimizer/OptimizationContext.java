package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class OptimizationContext {

    final LengauerTarjan tarjan;
    final ArrayList<Instruction> instructions;
    private int nextLocalId;

    // Cached edge maps — allocated lazily on first dominator/dead-code call and
    // reused across all subsequent calls. Each call clears and refills the
    // existing IntLinkedOpenHashSet objects instead of allocating new ones.
    // Lazy creation matters: small functions may never need them at all, and
    // large functions don't need to pay an upfront O(n) put-loop in the
    // constructor before the first read.
    @Nullable Int2ObjectOpenHashMap<IntSet> dominatorForward;
    @Nullable Int2ObjectOpenHashMap<IntSet> dominatorBackward;
    @Nullable Int2ObjectOpenHashMap<IntSet> deadCodeForward;

    // ── O(1) value-frequency cache ─────────────────────────────────────────────
    // Maps every Object that appears in any data[] field of any instruction to
    // the number of times it appears across ALL instructions (ALL fields,
    // including data[0] / result). Used by OptimizationPass.isValueUsedAnywhere
    // to turn the O(n) isValueUsed(0,n,false,v) scan into an O(1) lookup.
    //
    // Object2IntOpenHashMap rather than HashMap<Object,Integer>: optimisation
    // passes hit this map on every mutation through inc/decCounts, and the
    // boxed-Integer version dominated allocation profiles on large functions.
    @Nullable Object2IntOpenHashMap<Object> globalValueCounts = null;

    // ── O(k) type-index cache ──────────────────────────────────────────────────
    // Maps each InstructionType to the sorted list of instruction indices that
    // have that type. Turns the O(n) scan in findInstructions() into O(k) where
    // k = number of instructions with the requested type.
    //
    // Object2ObjectOpenHashMap rather than HashMap so the per-call .get() lookup
    // also avoids the boxing overhead of HashMap.hash().
    @Nullable Object2ObjectOpenHashMap<InstructionType, IntArrayList> typeIndex = null;

    OptimizationContext(ArrayList<Instruction> instructions, int nextLocalId, LengauerTarjan tarjan) {
        this.instructions = instructions;
        this.nextLocalId = nextLocalId;
        this.tarjan = tarjan;
        // Edge maps and value-count caches are built lazily on first use. The
        // previous version pre-populated all three edge maps with N empty
        // IntLinkedOpenHashSet instances upfront, which was wasted work for
        // functions that never invoke a dominator/dead-code pass and was a
        // significant chunk of small-function compile time.
    }

    /** Ensures all cached edge maps exist and contain entries 0..n-1. */
    void ensureCachedMapCapacity() {
        int n = instructions.size();
        if(dominatorForward == null) {
            dominatorForward  = new Int2ObjectOpenHashMap<>(n + 1);
            dominatorBackward = new Int2ObjectOpenHashMap<>(n + 1);
            deadCodeForward   = new Int2ObjectOpenHashMap<>(n + 1);
        }
        for(int i = dominatorForward.size(); i < n; i++) {
            dominatorForward.put(i,  new IntLinkedOpenHashSet(2));
            dominatorBackward.put(i, new IntLinkedOpenHashSet(2));
            deadCodeForward.put(i,   new IntLinkedOpenHashSet(2));
        }
    }

    /**
     * Invalidates both O(n) caches.  Kept as an escape hatch for callers that
     * cannot describe their mutation incrementally.
     */
    void invalidateCaches() {
        globalValueCounts = null;
        typeIndex = null;
    }

    /** Increment globalValueCounts for every non-null value in {@code data}. */
    private void incCounts(@Nullable Object[] data) {
        if(globalValueCounts == null) return;
        var counts = globalValueCounts;
        for(var d : data) {
            if(d != null) counts.addTo(d, 1);
        }
    }

    /** Decrement globalValueCounts for every non-null value in {@code data}. */
    private void decCounts(@Nullable Object[] data) {
        if(globalValueCounts == null) return;
        var counts = globalValueCounts;
        for(var d : data) {
            if(d == null) continue;
            int prev = counts.addTo(d, -1);
            // addTo returns the previous value; if it was 1 we just stored 0
            // and should remove the key entirely so the count map stays compact.
            if(prev <= 1) counts.removeInt(d);
        }
    }

    /**
     * Called after an instruction is inserted at {@code index}.
     * globalValueCounts is updated incrementally; typeIndex is invalidated
     * because every index >= {@code index} has shifted by +1.
     */
    void onInstructionAdded(int index, Instruction instr) {
        incCounts(instr.data());
        typeIndex = null;
    }

    /**
     * Called after an instruction is removed from {@code index}.
     * globalValueCounts is updated incrementally; typeIndex is invalidated
     * because every index > {@code index} has shifted by -1.
     */
    void onInstructionRemoved(int index, Instruction removed) {
        decCounts(removed.data());
        typeIndex = null;
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
        if(typeIndex != null && oldInstr.type() != newInstr.type()) {
            var oldList = typeIndex.get(oldInstr.type());
            if(oldList != null) {
                int pos = IntArrays.binarySearch(
                    oldList.elements(), 0, oldList.size(), index);
                if(pos >= 0) oldList.removeInt(pos);
            }
            var newList = typeIndex.computeIfAbsent(newInstr.type(), k -> new IntArrayList());
            int ins = IntArrays.binarySearch(
                newList.elements(), 0, newList.size(), index);
            if(ins < 0) ins = -ins - 1;
            newList.add(ins, index);
        }
    }

    /**
     * Returns (and lazily builds) the global value-frequency map.
     */
    Object2IntOpenHashMap<Object> getGlobalValueCounts() {
        if(globalValueCounts == null) {
            var counts = new Object2IntOpenHashMap<>();
            counts.defaultReturnValue(0);
            for(var instr : instructions) {
                for(var d : instr.data()) {
                    if(d != null) counts.addTo(d, 1);
                }
            }
            globalValueCounts = counts;
        }
        return globalValueCounts;
    }

    /**
     * Returns (and lazily builds) the type-to-indices index.
     * The IntArrayList for each type is in ascending index order.
     *
     * Reuses existing IntArrayList containers across rebuilds (clear instead
     * of realloc) when the cache was previously populated, since the same set
     * of types tends to recur across optimisation passes.
     */
    Object2ObjectOpenHashMap<InstructionType, IntArrayList> getTypeIndex() {
        if(typeIndex == null) {
            typeIndex = new Object2ObjectOpenHashMap<>();
        } else {
            // Lists are stale (the caller cleared the cache reference but the
            // map object survives across rebuilds in a future variant). Clear
            // them in place so we don't reallocate the backing arrays.
            for(var list : typeIndex.values()) list.clear();
        }
        int n = instructions.size();
        for(int i = 0; i < n; i++) {
            var type = instructions.get(i).type();
            var list = typeIndex.get(type);
            if(list == null) {
                list = new IntArrayList();
                typeIndex.put(type, list);
            }
            list.add(i);
        }
        return typeIndex;
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
