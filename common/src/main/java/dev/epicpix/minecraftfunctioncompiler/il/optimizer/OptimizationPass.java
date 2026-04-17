package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

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

    public @Nullable Instruction previousInstruction() {
        if(thisInstruction < 1) return null;
        return getInstruction(thisInstruction - 1);
    }

    // ── Value-use queries ──────────────────────────────────────────────────────

    /**
     * O(n) range scan — kept for callers that need a specific [start, end) window.
     * For global (0, n) checks prefer {@link #isValueUsedAnywhere}.
     */
    public boolean isValueUsed(int start, int end, boolean includeSelf, Object value) {
        for(int i = start; i < end; i++) {
            if(!includeSelf && i == thisInstruction) continue;
            if(getInstruction(i).containsValue(value)) return true;
        }
        return false;
    }

    /**
     * O(1) amortised — checks whether {@code value} appears in ANY data field of
     * ANY instruction other than the current one, using the cached global frequency
     * map in {@link OptimizationContext}.
     *
     * Semantically equivalent to {@code isValueUsed(0, n, false, value)} but avoids
     * the O(n) scan that made removeUnusedData O(n²) for large functions.
     */
    public boolean isValueUsedAnywhere(Object value) {
        var counts = context.getGlobalValueCounts();
        int total = counts.getOrDefault(value, 0);
        if(total == 0) return false;
        // Subtract the contribution of thisInstruction so we don't count self
        for(var d : thisInstruction().data()) {
            if(Objects.equals(d, value) && --total == 0) return false;
        }
        return total > 0;
    }

    // ── Instruction search ─────────────────────────────────────────────────────

    /**
     * For a single-predicate query, O(m) via the per-(type, argIndex) data
     * index where m = number of instructions whose data value matches.  For
     * multi-predicate queries, O(k) via the type index where k = number of
     * instructions of that type — the extra predicates are filtered inline.
     *
     * <p>The single-predicate fast path is what turns removeDuplicateChecks
     * (which calls {@code findInstructions(0, n, false, IF_NULL_JUMP,
     * [("local", X)])} once per IF_NULL_JUMP instruction) from O(K²) into
     * O(K), where K = number of IF_NULL_JUMPs in the function.
     */
    public IntList findInstructions(int start, int end, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        if(dataPairs.size() == 1) {
            var pair = dataPairs.get(0);
            int argIndex = type.getArgumentIndex(pair.first());
            var valueMatches = context.getDataIndex(type, argIndex).get(pair.second());
            if(valueMatches == null) return IntList.of();
            var results = new IntArrayList();
            for(var it = valueMatches.intIterator(); it.hasNext(); ) {
                int i = it.nextInt();
                if(i < start || i >= end) continue;
                if(i == thisInstruction && !includeSelf) continue;
                results.add(i);
            }
            return results;
        }
        var indexed = context.getTypeIndex().get(type);
        if(indexed == null) return IntList.of();
        var results = new IntArrayList();
        for(var it = indexed.intIterator(); it.hasNext(); ) {
            var i = it.nextInt();
            if(i < start || i >= end) continue;
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            boolean match = true;
            for(var data : dataPairs) {
                if(!Objects.equals(instruction.data()[type.getArgumentIndex(data.first())], data.second())) {
                    match = false;
                    break;
                }
            }
            if(match) results.add(i);
        }
        return results;
    }

    /**
     * Multi-type variant — uses type index for each requested type.
     */
    public IntList findInstructions(int start, int end, boolean includeSelf, List<InstructionType> types, List<Pair<String, Object>> dataPairs) {
        var typeIdx = context.getTypeIndex();
        var results = new IntArrayList();
        for(var type : types) {
            var indexed = typeIdx.get(type);
            if(indexed == null) continue;
            next:
            for(var it = indexed.intIterator(); it.hasNext(); ) {
                var i = it.nextInt();
                if(i < start || i >= end) continue;
                if(i == thisInstruction && !includeSelf) continue;
                var instruction = getInstruction(i);
                for(var data : dataPairs) {
                    if(!Objects.equals(instruction.data()[instruction.type().getArgumentIndex(data.first())], data.second())) {
                        continue next;
                    }
                }
                results.add(i);
            }
        }
        // Restore ascending order (results from different types may be interleaved)
        results.sort(null);
        return results;
    }

    public @Nullable Integer findSingleInstruction(int start, int end, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var instrs = findInstructions(start, end, includeSelf, type, dataPairs);
        if(instrs.size() != 1) return null;
        return instrs.getInt(0);
    }

    public IntList findInstructionsIn(IntList instructions, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var results = new IntArrayList();
        next:
        for(IntIterator it = instructions.intIterator(); it.hasNext(); ) {
            var i = it.nextInt();
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            if(instruction.type() != type) continue;
            for(var data : dataPairs) {
                if(!Objects.equals(instruction.data()[type.getArgumentIndex(data.first())], data.second())) {
                    continue next;
                }
            }
            results.add(i);
        }
        return results;
    }

    // ── Edge graph ─────────────────────────────────────────────────────────────

    public void generateInstructionEdges(Int2ObjectMap<IntSet> forward, @Nullable Int2ObjectMap<IntSet> backward) {
        var labelLocations = new Int2IntOpenHashMap();
        int n = context.getInstructionCount();
        for(int i = 0; i < n; i++) {
            var instruction = getInstruction(i);
            if(instruction.type() == InstructionTypes.LABEL) {
                labelLocations.put(Objects.requireNonNull(instruction.<LabelData>get(0)).id(), i);
            }
            if(backward != null) {
                backward.computeIfAbsent(i, k -> new IntLinkedOpenHashSet(2)).clear();
            }
        }

        for(int i = 0; i < n; i++) {
            IntSet set = forward.computeIfAbsent(i, k -> new IntLinkedOpenHashSet(2));
            set.clear();
            var instruction = getInstruction(i);

            if(instruction.type().isConditionJump() || instruction.type() == InstructionTypes.JUMP) {
                for(var value : instruction.data()) {
                    if(value instanceof LabelData(int id)) {
                        var loc = labelLocations.get(id);
                        set.add(loc);
                        if(backward != null) backward.get(loc).add(i);
                    }
                }
            }
            if(instruction.type() != InstructionTypes.JUMP
                    && instruction.type() != InstructionTypes.RETURN_SUCCESS
                    && instruction.type() != InstructionTypes.RETURN_FAIL
                    && i != n - 1) {
                set.add(i + 1);
                if(backward != null) backward.get(i + 1).add(i);
            }
        }
    }

    // ── Dominator queries ──────────────────────────────────────────────────────

    public IntList findExecutedInstructions() {
        return findExecutedInstructionsUntil(thisInstruction);
    }

    public IntList findExecutedInstructionsUntil(int index) {
        if(shouldEvaluateNewInstructionPaths) {
            context.ensureCachedMapCapacity();
            generateInstructionEdges(context.dominatorForward, context.dominatorBackward);
            context.tarjan.updateData(context.dominatorForward, context.dominatorBackward);
            shouldEvaluateNewInstructionPaths = false;
        }
        return context.tarjan.getDominators(index);
    }

    // ── Bulk data replacement ──────────────────────────────────────────────────

    public void replaceInstructionData(boolean includeSelf, Object from, Object to) {
        // Fast exit via the global value-count cache: if the value being
        // searched-and-replaced doesn't appear in any instruction's data[]
        // anywhere, the O(n) scan below has nothing to do.  Most callers in
        // the optimizer use this to clean up already-rewritten or already-
        // removed values, so the early-exit path is the common case.
        var counts = context.getGlobalValueCounts();
        int total = counts.getOrDefault(from, 0);
        if(total == 0) return;
        if(!includeSelf) {
            // Subtract the current instruction's contribution to total —
            // if all occurrences live on `thisInstruction` (which we'd skip
            // anyway), there is nothing for the scan to do.
            int selfContribution = 0;
            for(var d : thisInstruction().data()) {
                if(Objects.equals(d, from)) selfContribution++;
            }
            if(selfContribution >= total) return;
        }
        for(int i = 0; i < context.getInstructionCount(); i++) {
            if(!includeSelf && i == thisInstruction) continue;
            var instr = getInstruction(i);
            var replacedInstructionData = instr.optReplaceData(from, to);
            if(replacedInstructionData.isPresent()) {
                modifyInstructionData(i, replacedInstructionData.get());
            }
        }
    }

    public OptimizationContext context() {
        return context;
    }

    // ── Instruction mutation — all invalidate the O(n) caches ─────────────────

    private boolean shouldEvaluateNewInstructionPaths = true;

    public void addInstruction(int index, Instruction instruction) {
        context.instructions.add(index, instruction);
        context.onInstructionAdded(index, instruction);
        resetIndex = Math.min(resetIndex, index);
        shouldEvaluateNewInstructionPaths = true;
    }

    public void removeInstruction(int index) {
        var removed = context.instructions.remove(index);
        context.onInstructionRemoved(index, removed);
        resetIndex = Math.min(resetIndex, index);
        shouldEvaluateNewInstructionPaths = true;
    }

    public void replaceInstruction(int index, Instruction instruction) {
        var prev = context.instructions.set(index, instruction);
        context.onInstructionReplaced(index, prev, instruction);
        resetIndex = Math.min(resetIndex, index);
        var oldIsEnding = prev.type() == InstructionTypes.JUMP || prev.type() == InstructionTypes.RETURN_SUCCESS || prev.type() == InstructionTypes.RETURN_FAIL;
        var newIsEnding = instruction.type() == InstructionTypes.JUMP || instruction.type() == InstructionTypes.RETURN_SUCCESS || instruction.type() == InstructionTypes.RETURN_FAIL;
        if(oldIsEnding || newIsEnding || prev.type().isConditionJump() || instruction.type().isConditionJump()) {
            if(!shouldEvaluateNewInstructionPaths && prev.type() == instruction.type()) {
                var oldData = prev.data();
                var newData = instruction.data();
                for(int i = 0; i < newData.length; i++) {
                    if(oldData[i] == newData[i]) continue;
                    if(oldData[i] instanceof LabelData || newData[i] instanceof LabelData) {
                        shouldEvaluateNewInstructionPaths = true;
                        return;
                    }
                }
            }
            shouldEvaluateNewInstructionPaths = true;
        }
    }

    public void modifyInstructionData(int index, @Nullable Object[] instructionData) {
        var instr = context.instructions.get(index);
        replaceInstruction(index, new Instruction(instr.type(), instructionData));
        resetIndex = Math.min(resetIndex, index);
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
