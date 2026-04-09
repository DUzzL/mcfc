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
