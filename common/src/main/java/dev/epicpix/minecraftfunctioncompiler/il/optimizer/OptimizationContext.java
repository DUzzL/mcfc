package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Mutable state of one optimizer run.
 *
 * <p>Instructions are removed by overwriting them with {@link #REMOVED_INSTRUCTION} so indices stay stable
 * while a pass runs; {@link #compact()} drops the placeholders between passes. Because indices are stable, every
 * index below (type index, data index, value occurrences, CFG, dominator tree) survives removals and only needs
 * a rebuild after {@link #compact()} or a control-flow change.
 */
public class OptimizationContext {

    /** Placeholder for a removed instruction; it falls through and is never emitted. */
    static final InstructionType REMOVED = new InstructionType("$REMOVED", new String[0], 0);
    static final Instruction REMOVED_INSTRUCTION = new Instruction(REMOVED, new Object[0]);

    final LengauerTarjan tarjan;
    final ArrayList<Instruction> instructions;
    private int nextLocalId;
    private int removedCount;

    // ── Value-frequency caches (maintained incrementally) ──────────────────────
    // value -> number of data slots (all instructions, all fields) holding it.
    @Nullable HashMap<Object, Integer> globalValueCounts = null;
    // value -> number of instructions whose "result" slot holds it (i.e. number of definitions).
    @Nullable HashMap<Object, Integer> resultValueCounts = null;

    // ── Index caches (index-stable, rebuilt after compact()) ──────────────────
    // type -> ascending instruction indices of that type.
    @Nullable HashMap<InstructionType, IntArrayList> typeIndex = null;
    // (type, argIndex) -> value -> ascending instruction indices with that value at argIndex.
    @Nullable HashMap<DataIndexKey, HashMap<@Nullable Object, IntArrayList>> dataIndex = null;
    // value -> instruction indices that held the value in some slot at some point (superset; may repeat).
    private @Nullable HashMap<Object, IntArrayList> valueOccurrences = null;
    // value -> index of the last instruction that defined it through its "result" slot (may be stale).
    private @Nullable HashMap<Object, Integer> definitionIndex = null;

    record DataIndexKey(InstructionType type, int argIndex) {}

    // ── Control-flow graph (CSR) + dominator tree ──────────────────────────────
    private boolean cfgValid = false;
    private int cfgSize;
    private int[] succOff = new int[1];
    private int[] succ = new int[0];
    private int[] predOff = new int[1];
    private int[] pred = new int[0];
    // Lazily derived from the CFG, see reachesItselfAvoiding.
    private int[] backSpan = new int[0];
    private boolean backSpanValid = false;

    private final HashMap<InstructionType, Integer> typeResultArgIdx = new HashMap<>();

    OptimizationContext(ArrayList<Instruction> instructions, int nextLocalId, LengauerTarjan tarjan) {
        this.instructions = instructions;
        this.nextLocalId = nextLocalId;
        this.tarjan = tarjan;
    }

    private int resultArgIdxOf(InstructionType type) {
        Integer cached = typeResultArgIdx.get(type);
        if(cached != null) return cached;
        int idx = -1;
        String[] names = type.argumentNames();
        for(int i = 0; i < names.length; i++) {
            if("result".equals(names[i])) {
                idx = i;
                break;
            }
        }
        typeResultArgIdx.put(type, idx);
        return idx;
    }

    int resultArgIndex(InstructionType type) {
        return resultArgIdxOf(type);
    }

    static boolean isRemoved(Instruction instruction) {
        return instruction.type() == REMOVED;
    }

    static boolean endsBlock(InstructionType type) {
        return type == InstructionTypes.JUMP || type == InstructionTypes.RETURN_SUCCESS || type == InstructionTypes.RETURN_FAIL;
    }

    static boolean hasLabelOperand(Instruction instruction) {
        if(instruction.type() == InstructionTypes.LABEL) return false;
        for(var d : instruction.data()) {
            if(d instanceof LabelData) return true;
        }
        return false;
    }

    // ── Mutation ───────────────────────────────────────────────────────────────

    /** Replaces instruction {@code index}; returns the previous one. Keeps every cache consistent. */
    Instruction replace(int index, Instruction newInstr) {
        var oldInstr = instructions.set(index, newInstr);
        if(isRemoved(newInstr) && !isRemoved(oldInstr)) removedCount++;
        else if(isRemoved(oldInstr) && !isRemoved(newInstr)) removedCount--;

        if(globalValueCounts != null) {
            decCounts(oldInstr.data());
            incCounts(newInstr.data());
        }
        if(resultValueCounts != null) {
            decResult(oldInstr);
            incResult(newInstr);
        }
        if(typeIndex != null && oldInstr.type() != newInstr.type()) {
            removeSorted(typeIndex.get(oldInstr.type()), index);
            addSorted(typeIndex.computeIfAbsent(newInstr.type(), k -> new IntArrayList()), index);
        }
        if(dataIndex != null) {
            for(var entry : dataIndex.entrySet()) {
                var key = entry.getKey();
                if(key.type() == oldInstr.type()) removeSorted(entry.getValue().get(oldInstr.data()[key.argIndex()]), index);
                if(key.type() == newInstr.type()) addSorted(entry.getValue().computeIfAbsent(newInstr.data()[key.argIndex()], k -> new IntArrayList()), index);
            }
        }
        if(valueOccurrences != null) {
            for(var d : newInstr.data()) {
                if(d != null) valueOccurrences.computeIfAbsent(d, k -> new IntArrayList(2)).add(index);
            }
        }
        if(definitionIndex != null) {
            int r = resultArgIdxOf(newInstr.type());
            if(r >= 0 && newInstr.data()[r] != null) definitionIndex.put(newInstr.data()[r], index);
        }
        if(cfgValid && changesControlFlow(oldInstr, newInstr)) cfgValid = false;
        return oldInstr;
    }

    /**
     * Whether swapping {@code oldInstr} for {@code newInstr} can make the cached CFG unsound. Removing edges keeps
     * the cached dominator tree conservative (dominance only grows when paths disappear), so turning a conditional
     * jump into a fall-through placeholder does not invalidate it; anything that can add or retarget an edge does.
     */
    private static boolean changesControlFlow(Instruction oldInstr, Instruction newInstr) {
        var oldType = oldInstr.type();
        var newType = newInstr.type();
        if(endsBlock(oldType) || endsBlock(newType)) {
            if(oldType != newType) return true;
            return labelsDiffer(oldInstr, newInstr);
        }
        if(newType == InstructionTypes.LABEL || oldType == InstructionTypes.LABEL) {
            return oldType != newType || labelsDiffer(oldInstr, newInstr);
        }
        boolean oldJumps = hasLabelOperand(oldInstr);
        boolean newJumps = hasLabelOperand(newInstr);
        if(!newJumps) return false; // at most removes edges
        if(!oldJumps) return true;
        return labelsDiffer(oldInstr, newInstr);
    }

    private static boolean labelsDiffer(Instruction a, Instruction b) {
        var ad = a.data();
        var bd = b.data();
        if(ad.length != bd.length) return true;
        for(int i = 0; i < ad.length; i++) {
            if((ad[i] instanceof LabelData || bd[i] instanceof LabelData) && !Objects.equals(ad[i], bd[i])) return true;
        }
        return false;
    }

    void remove(int index) {
        replace(index, REMOVED_INSTRUCTION);
    }

    /** Drops removed placeholders. Returns true if anything was dropped (all index caches are then rebuilt lazily). */
    boolean compact() {
        if(removedCount == 0) return false;
        int w = 0;
        for(int r = 0, n = instructions.size(); r < n; r++) {
            var instr = instructions.get(r);
            if(!isRemoved(instr)) instructions.set(w++, instr);
        }
        instructions.subList(w, instructions.size()).clear();
        removedCount = 0;
        invalidateIndices();
        return true;
    }

    private void invalidateIndices() {
        typeIndex = null;
        dataIndex = null;
        valueOccurrences = null;
        definitionIndex = null;
        cfgValid = false;
    }

    private static void removeSorted(@Nullable IntArrayList list, int value) {
        if(list == null) return;
        int pos = IntArrays.binarySearch(list.elements(), 0, list.size(), value);
        if(pos >= 0) list.removeInt(pos);
    }

    private static void addSorted(IntArrayList list, int value) {
        int pos = IntArrays.binarySearch(list.elements(), 0, list.size(), value);
        if(pos < 0) list.add(-pos - 1, value);
    }

    private void incCounts(@Nullable Object[] data) {
        if(globalValueCounts == null) return;
        for(var d : data) {
            if(d != null) globalValueCounts.merge(d, 1, Integer::sum);
        }
    }

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

    private void incResult(Instruction instr) {
        if(resultValueCounts == null) return;
        int idx = resultArgIdxOf(instr.type());
        if(idx < 0) return;
        Object v = instr.data()[idx];
        if(v != null) resultValueCounts.merge(v, 1, Integer::sum);
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

    // ── Queries ────────────────────────────────────────────────────────────────

    /** value -> number of instructions defining it through their "result" slot. */
    HashMap<Object, Integer> getResultValueCounts() {
        if(resultValueCounts == null) {
            resultValueCounts = new HashMap<>();
            for(var instr : instructions) incResult(instr);
        }
        return resultValueCounts;
    }

    int definitionCount(Object value) {
        return getResultValueCounts().getOrDefault(value, 0);
    }

    /** Index of the only instruction defining {@code value}, or -1 if there is not exactly one. */
    int singleDefinitionIndex(Object value) {
        if(definitionCount(value) != 1) return -1;
        if(definitionIndex == null) {
            definitionIndex = new HashMap<>();
            for(int i = 0, n = instructions.size(); i < n; i++) {
                var instr = instructions.get(i);
                int r = resultArgIdxOf(instr.type());
                if(r >= 0 && instr.data()[r] != null) definitionIndex.put(instr.data()[r], i);
            }
        }
        Integer idx = definitionIndex.get(value);
        if(idx == null) return -1;
        var instr = instructions.get(idx);
        int r = resultArgIdxOf(instr.type());
        if(r < 0 || !Objects.equals(instr.data()[r], value)) return -1;
        return idx;
    }

    /** value -> number of data slots holding it. */
    HashMap<Object, Integer> getGlobalValueCounts() {
        if(globalValueCounts == null) {
            globalValueCounts = new HashMap<>();
            for(var instr : instructions) incCounts(instr.data());
        }
        return globalValueCounts;
    }

    /**
     * Indices of instructions that may contain {@code value} (a superset: callers must re-check the instruction).
     * The returned list is live; copy it before mutating instructions.
     */
    @Nullable IntArrayList valueOccurrences(Object value) {
        if(valueOccurrences == null) {
            valueOccurrences = new HashMap<>();
            for(int i = 0, n = instructions.size(); i < n; i++) {
                for(var d : instructions.get(i).data()) {
                    if(d != null) valueOccurrences.computeIfAbsent(d, k -> new IntArrayList(2)).add(i);
                }
            }
        }
        return valueOccurrences.get(value);
    }

    HashMap<InstructionType, IntArrayList> getTypeIndex() {
        if(typeIndex == null) {
            typeIndex = new HashMap<>();
            for(int i = 0, n = instructions.size(); i < n; i++) {
                typeIndex.computeIfAbsent(instructions.get(i).type(), k -> new IntArrayList()).add(i);
            }
        }
        return typeIndex;
    }

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
                map.computeIfAbsent(instructions.get(i).data()[argIndex], k -> new IntArrayList()).add(i);
            }
        }
        dataIndex.put(key, map);
        return map;
    }

    // ── CFG / dominators ───────────────────────────────────────────────────────

    /**
     * Builds the CFG: every instruction falls through unless it is JUMP/RETURN_*, and every LabelData operand of a
     * non-LABEL instruction is an edge (instruction types carry no jump flag; a label operand is the jump target).
     */
    private void buildCfg() {
        int n = instructions.size();
        var labelLocations = new Int2IntOpenHashMap();
        labelLocations.defaultReturnValue(-1);
        for(int i = 0; i < n; i++) {
            var instr = instructions.get(i);
            if(instr.type() == InstructionTypes.LABEL) {
                int id = Objects.requireNonNull(instr.<LabelData>get(0)).id();
                if(labelLocations.put(id, i) != -1) throw new IllegalStateException("Duplicate label " + id);
            }
        }
        if(succOff.length < n + 1) {
            succOff = new int[n + 1];
            predOff = new int[n + 1];
        }
        // pass 1: out-degrees
        int edges = 0;
        for(int i = 0; i < n; i++) {
            var instr = instructions.get(i);
            succOff[i] = edges;
            if(instr.type() != InstructionTypes.LABEL) {
                for(var d : instr.data()) if(d instanceof LabelData) edges++;
            }
            if(!endsBlock(instr.type()) && i != n - 1) edges++;
        }
        succOff[n] = edges;
        if(succ.length < edges) {
            succ = new int[edges];
            pred = new int[edges];
        }
        java.util.Arrays.fill(predOff, 0, n + 1, 0);
        // pass 2: targets
        int e = 0;
        for(int i = 0; i < n; i++) {
            var instr = instructions.get(i);
            if(instr.type() != InstructionTypes.LABEL) {
                for(var d : instr.data()) {
                    if(d instanceof LabelData(int id)) {
                        int target = labelLocations.get(id);
                        if(target < 0) throw new IllegalStateException("Jump to missing label " + id + " in " + instr);
                        succ[e++] = target;
                        predOff[target + 1]++;
                    }
                }
            }
            if(!endsBlock(instr.type()) && i != n - 1) {
                succ[e++] = i + 1;
                predOff[i + 2]++;
            }
        }
        for(int i = 0; i < n; i++) predOff[i + 1] += predOff[i];
        int[] fill = java.util.Arrays.copyOf(predOff, n);
        for(int i = 0; i < n; i++) {
            for(int k = succOff[i]; k < succOff[i + 1]; k++) {
                int t = succ[k];
                pred[fill[t]++] = i;
            }
        }
        cfgSize = n;
        backSpanValid = false;
    }

    private void ensureDominators() {
        if(cfgValid) return;
        buildCfg();
        tarjan.compute(cfgSize, succOff, succ, predOff, pred);
        cfgValid = true;
    }

    /** True iff every path from the entry to {@code b} passes {@code a}. Conservative (false) for unreachable code. */
    boolean dominates(int a, int b) {
        ensureDominators();
        return tarjan.dominates(a, b);
    }

    /**
     * Whether {@code node} can be reached again from itself on a path that avoids {@code blocked}. Conservative
     * (true) while edges removed since the last CFG build are still cached. Fall-through edges only go forward, so a
     * cycle through {@code node} needs a backward edge spanning it; the walk only runs when one exists.
     */
    boolean reachesItselfAvoiding(int node, int blocked) {
        ensureDominators();
        if(!backSpanValid) computeBackSpan();
        if(backSpan[node] == 0) return false;
        var seen = new boolean[cfgSize];
        var stack = new int[cfgSize];
        int sp = 0;
        for(int k = succOff[node]; k < succOff[node + 1]; k++) {
            int t = succ[k];
            if(t != blocked && !seen[t]) {
                seen[t] = true;
                stack[sp++] = t;
            }
        }
        while(sp > 0) {
            int v = stack[--sp];
            if(v == node) return true;
            for(int k = succOff[v]; k < succOff[v + 1]; k++) {
                int t = succ[k];
                if(t != blocked && !seen[t]) {
                    seen[t] = true;
                    stack[sp++] = t;
                }
            }
        }
        return false;
    }

    /** backSpan[i] = number of backward (or self) edges u -> w with w <= i <= u. */
    private void computeBackSpan() {
        int n = cfgSize;
        if(backSpan.length < n + 1) backSpan = new int[n + 1];
        java.util.Arrays.fill(backSpan, 0, n + 1, 0);
        for(int u = 0; u < n; u++) {
            for(int k = succOff[u]; k < succOff[u + 1]; k++) {
                int w = succ[k];
                if(w <= u) {
                    backSpan[w]++;
                    backSpan[u + 1]--;
                }
            }
        }
        for(int i = 1; i < n; i++) backSpan[i] += backSpan[i - 1];
        backSpanValid = true;
    }

    /** Number of CFG predecessors of {@code node} (an upper bound after edges were removed since the last rebuild). */
    int predecessorCount(int node) {
        ensureDominators();
        return predOff[node + 1] - predOff[node];
    }

    /** Replaces every instruction not reachable from the entry with the removed placeholder. */
    void removeUnreachable() {
        buildCfg();
        int n = cfgSize;
        if(n == 0) return;
        var reached = new boolean[n];
        var stack = new int[n];
        int sp = 0;
        stack[sp++] = 0;
        reached[0] = true;
        while(sp > 0) {
            int v = stack[--sp];
            for(int k = succOff[v]; k < succOff[v + 1]; k++) {
                int t = succ[k];
                if(!reached[t]) {
                    reached[t] = true;
                    stack[sp++] = t;
                }
            }
        }
        for(int i = 0; i < n; i++) {
            if(!reached[i] && !isRemoved(instructions.get(i))) remove(i);
        }
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

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
