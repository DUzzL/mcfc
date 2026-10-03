package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * View of the optimizer state for the instruction currently being optimized. Removals keep indices stable
 * (see {@link OptimizationContext}); removed slots are skipped by every query here.
 */
public final class OptimizationPass {

    private int resetIndex = Integer.MAX_VALUE;
    private int thisInstruction;

    private final OptimizationContext context;

    public OptimizationPass(int thisInstruction, OptimizationContext context) {
        this.thisInstruction = thisInstruction;
        this.context = context;
    }

    public int thisInstructionIndex() {
        return thisInstruction;
    }

    public Instruction getInstruction(int index) {
        return context.getInstruction(index);
    }

    public Instruction thisInstruction() {
        return getInstruction(thisInstruction);
    }

    /** Index of the closest preceding instruction that was not removed, or -1. */
    public int previousInstructionIndex() {
        for(int i = thisInstruction - 1; i >= 0; i--) {
            if(!OptimizationContext.isRemoved(getInstruction(i))) return i;
        }
        return -1;
    }

    /** Index of the closest following instruction (after {@code index}) that was not removed, or -1. */
    public int nextInstructionIndex(int index) {
        for(int i = index + 1, n = context.getInstructionCount(); i < n; i++) {
            if(!OptimizationContext.isRemoved(getInstruction(i))) return i;
        }
        return -1;
    }

    public OptimizationContext context() {
        return context;
    }

    // ── Value-use queries ──────────────────────────────────────────────────────

    /** Whether {@code value} appears in any data slot of any instruction other than the current one. O(1). */
    public boolean isValueUsedAnywhere(Object value) {
        int total = context.getGlobalValueCounts().getOrDefault(value, 0);
        if(total == 0) return false;
        for(var d : thisInstruction().data()) {
            if(Objects.equals(d, value) && --total == 0) return false;
        }
        return total > 0;
    }

    // ── Instruction search ─────────────────────────────────────────────────────

    /**
     * Ascending indices of every {@code type} instruction whose {@code argument} equals {@code value} (including this
     * one). The list is live: copy it before mutating instructions.
     */
    public IntList instructionsWith(InstructionType type, String argument, @Nullable Object value) {
        var matches = context.getDataIndex(type, type.getArgumentIndex(argument)).get(value);
        return matches == null ? IntList.of() : matches;
    }

    // ── Dominator queries ──────────────────────────────────────────────────────

    /** True iff every path from the function entry to instruction {@code b} passes instruction {@code a}. */
    public boolean dominates(int a, int b) {
        return context.dominates(a, b);
    }

    // ── Bulk data replacement ──────────────────────────────────────────────────

    /** Replaces {@code from} with {@code to} in every data slot of every instruction (optionally except this one). */
    public void replaceInstructionData(boolean includeSelf, Object from, Object to) {
        if(from.equals(to)) return;
        if(context.getGlobalValueCounts().getOrDefault(from, 0) == 0) return;
        var occurrences = context.valueOccurrences(from);
        if(occurrences == null) return;
        // replace() appends to the occurrence lists of the new values only, so iterating a copy is enough.
        var snapshot = occurrences.toIntArray();
        for(int i : snapshot) {
            if(!includeSelf && i == thisInstruction) continue;
            var replaced = getInstruction(i).optReplaceData(from, to);
            if(replaced.isPresent()) modifyInstructionData(i, replaced.get());
        }
    }

    // ── Instruction mutation ───────────────────────────────────────────────────

    /** Removes an instruction without shifting indices. */
    public void removeInstruction(int index) {
        context.remove(index);
        resetIndex = Math.min(resetIndex, index);
    }

    public void replaceInstruction(int index, Instruction instruction) {
        context.replace(index, instruction);
        resetIndex = Math.min(resetIndex, index);
    }

    public void modifyInstructionData(int index, @Nullable Object[] instructionData) {
        replaceInstruction(index, new Instruction(context.getInstruction(index).type(), instructionData));
    }

    public int takeResetIndex() {
        int index = resetIndex;
        resetIndex = Integer.MAX_VALUE;
        return index;
    }

    void setThisInstruction(int index) {
        thisInstruction = index;
    }
}
