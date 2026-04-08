package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class OptimizationContext {

    final LengauerTarjan tarjan;
    final ArrayList<Instruction> instructions;
    private int nextLocalId;

    // Cached edge maps — allocated once per optimization run and reused across all
    // Tarjan calls. Each call clears and refills the existing IntLinkedOpenHashSet
    // objects instead of allocating new ones, eliminating O(n) allocations per call.
    // dominatorForward/Backward are used by findExecutedInstructionsUntil.
    // deadCodeForward is used by removeDeadCodeQuick (backward edges not needed there).
    Int2ObjectOpenHashMap<IntSet> dominatorForward;
    Int2ObjectOpenHashMap<IntSet> dominatorBackward;
    Int2ObjectOpenHashMap<IntSet> deadCodeForward;

    OptimizationContext(ArrayList<Instruction> instructions, int nextLocalId, LengauerTarjan tarjan) {
        this.instructions = instructions;
        this.nextLocalId = nextLocalId;
        this.tarjan = tarjan;
        int n = instructions.size();
        dominatorForward  = new Int2ObjectOpenHashMap<>(n + 1);
        dominatorBackward = new Int2ObjectOpenHashMap<>(n + 1);
        deadCodeForward   = new Int2ObjectOpenHashMap<>(n + 1);
        for(int i = 0; i < n; i++) {
            dominatorForward.put(i,  new IntLinkedOpenHashSet(2));
            dominatorBackward.put(i, new IntLinkedOpenHashSet(2));
            deadCodeForward.put(i,   new IntLinkedOpenHashSet(2));
        }
    }

    /** Ensure all cached maps contain entries 0..n-1 (grows if instructions were added). */
    void ensureCachedMapCapacity() {
        int n = instructions.size();
        while(dominatorForward.size() < n) {
            int idx = dominatorForward.size();
            dominatorForward.put(idx,  new IntLinkedOpenHashSet(2));
            dominatorBackward.put(idx, new IntLinkedOpenHashSet(2));
            deadCodeForward.put(idx,   new IntLinkedOpenHashSet(2));
        }
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
