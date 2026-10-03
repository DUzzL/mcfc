package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * IL optimizations. Soundness rules shared by all of them:
 * <ul>
 *   <li>A value may only be forwarded from one local to another when both locals have exactly one definition
 *       (the IL is not SSA: loop counters and similar locals are assigned more than once).</li>
 *   <li>Dominance comes from {@link OptimizationPass#dominates}; the cached tree is only ever conservative.</li>
 * </ul>
 */
public final class Optimizations {
    private Optimizations() {}

    /** How many earlier same-local null checks removeDuplicateChecks tries before giving up. */
    private static final int MAX_DUPLICATE_CHECK_CANDIDATES = 8;

    public static final List<Optimization> OPTIMIZATIONS = List.of(
        Optimizations::mergeValues,
        Optimizations::removeDuplicateChecks,
        Optimizations::inlineLists,
        Optimizations::mergeLabels,
        Optimizations::removeUselessCommandStackData,
        Optimizations::removeDeadComparisons,
        Optimizations::removeUnusedData
    );

    /** Index of the first element {@code >= value} in an ascending list. */
    private static int lowerBound(IntList sorted, int value) {
        int lo = 0;
        int hi = sorted.size();
        while(lo < hi) {
            int mid = (lo + hi) >>> 1;
            if(sorted.getInt(mid) < value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /**
     * Collapses every chain of adjacent LABEL instructions (removed placeholders in between are ignored) into its
     * first label: references are rewritten in one sweep, then the redundant LABELs are removed. O(n).
     */
    static void bulkMergeAdjacentLabels(OptimizationPass pass) {
        var ctx = pass.context();
        int n = ctx.getInstructionCount();
        if(n < 2) return;

        HashMap<Integer, LabelData> forwarding = null;
        LabelData prevLabel = null;
        for(int i = 0; i < n; i++) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL) {
                var ld = Objects.requireNonNull(instr.<LabelData>get(0));
                if(prevLabel != null) {
                    if(forwarding == null) forwarding = new HashMap<>();
                    // prevLabel may itself be forwarded (chains of 3+): forward to the chain's root.
                    forwarding.put(ld.id(), forwarding.getOrDefault(prevLabel.id(), prevLabel));
                }
                prevLabel = ld;
            }else if(!OptimizationContext.isRemoved(instr)) {
                prevLabel = null;
            }
        }
        if(forwarding == null) return;

        // Rewrite references (LABEL definitions themselves are left alone and removed below).
        for(int i = 0; i < n; i++) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL) continue;
            var data = instr.data();
            @Nullable Object[] newData = null;
            for(int d = 0; d < data.length; d++) {
                if(data[d] instanceof LabelData(int id)) {
                    var to = forwarding.get(id);
                    if(to != null) {
                        if(newData == null) newData = data.clone();
                        newData[d] = to;
                    }
                }
            }
            if(newData != null) pass.modifyInstructionData(i, newData);
        }

        for(int i = 0; i < n; i++) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL && forwarding.containsKey(Objects.requireNonNull(instr.<LabelData>get(0)).id())) {
                pass.removeInstruction(i);
            }
        }
    }

    private static void mergeLabels(OptimizationPass pass) {
        // Adjacent label pairs are merged in bulk once per round (per-pair merging was O(pairs × n)).
        if(pass.thisInstructionIndex() == 0) {
            bulkMergeAdjacentLabels(pass);
        }

        if(pass.thisInstruction().type() != InstructionTypes.LABEL) return;
        var thisLabel = Objects.requireNonNull(pass.thisInstruction().<LabelData>get("label"));

        int prevIndex = pass.previousInstructionIndex();
        var prevInstruction = prevIndex < 0 ? null : pass.getInstruction(prevIndex);
        // A pair created after this round's bulk merge is left for the next round's bulk merge.
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes.LABEL) return;

        // A LabelData value appears once in its LABEL and once per jump to it.
        int useCount = pass.context().getGlobalValueCounts().getOrDefault(thisLabel, 0) - 1;
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes.JUMP && Objects.equals(prevInstruction.get("label"), thisLabel)) {
            if(useCount == 1) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
            pass.removeInstruction(prevIndex);
        }else if(useCount == 0) {
            pass.removeInstruction(pass.thisInstructionIndex());
        }
    }

    private static void removeUselessCommandStackData(OptimizationPass pass) {
        var ctx = pass.context();
        var thisInstr = pass.thisInstruction();
        if(thisInstr.type() == InstructionTypes.GET_STACK_ENTITY) {
            // GET_STACK_ENTITY(stack) where stack = MAKE_COMMAND_SOURCE_STACK(entity=e, ...) is just e.
            var stack = thisInstr.get("stack");
            if(stack == null) return;
            int def = ctx.singleDefinitionIndex(stack);
            if(def < 0) return;
            var defInstr = pass.getInstruction(def);
            if(defInstr.type() != InstructionTypes.MAKE_COMMAND_SOURCE_STACK) return;
            Object entitySource = defInstr.get("entity");
            if(entitySource == null || ctx.definitionCount(entitySource) > 1) return;
            var result = Objects.requireNonNull(thisInstr.get("result"));
            if(ctx.definitionCount(result) != 1) return;

            pass.replaceInstructionData(false, result, entitySource);
            pass.removeInstruction(pass.thisInstructionIndex());
        }else if(thisInstr.type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
            if(thisInstr.get("anchor") == null && thisInstr.get("entity") == null && thisInstr.get("position") == null && thisInstr.get("rotation") == null && thisInstr.get("world") == null) {
                var result = Objects.requireNonNull(thisInstr.get("result"));
                var from = Objects.requireNonNull(thisInstr.get("from"));
                if(ctx.definitionCount(result) != 1 || ctx.definitionCount(from) > 1) return;
                pass.replaceInstructionData(false, result, from);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void mergeValues(OptimizationPass pass) {
        mergePureData(pass, InstructionTypes.GET_STACK_ENTITY, "stack", "result");
        mergePureData(pass, InstructionTypes.GET_SCOREBOARD_NAME, "entity", "result");

        var thisInstr = pass.thisInstruction();
        if(thisInstr.type() == InstructionTypes2.CALL_FUNCTIONS) {
            int prevIndex = pass.previousInstructionIndex();
            if(prevIndex < 0) return;
            var prev = pass.getInstruction(prevIndex);
            if(prev.type() == InstructionTypes2.CALL_FUNCTIONS
                    && Objects.equals(thisInstr.get("arguments"), prev.get("arguments"))
                    && Objects.equals(thisInstr.get("stack"), prev.get("stack"))) {
                var functions = new ArrayList<DataLocation>(Objects.requireNonNull(prev.get("functions")));
                functions.addAll(Objects.requireNonNull(thisInstr.get("functions")));
                pass.replaceInstruction(prevIndex, Instructions2.callFunctions(functions, prev.get("stack"), prev.get("arguments")));
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }else if(thisInstr.type() == InstructionTypes.MERGE_POSITIONS) {
            if(thisInstr.get("x") != null && thisInstr.get("y") != null && thisInstr.get("z") != null) {
                pass.replaceInstruction(pass.thisInstructionIndex(), Instructions.createPosition(thisInstr.get("result"), thisInstr.get("x"), thisInstr.get("y"), thisInstr.get("z")));
            }
        }
    }

    /**
     * Global value numbering for one pure instruction type: if an instruction computing the same value from the
     * same (single-definition) operand dominates this one, reuse its result.
     */
    private static void mergePureData(OptimizationPass pass, InstructionType type, String compare, String replace) {
        if(pass.thisInstruction().type() != type) return;
        var ctx = pass.context();
        var compareData = pass.thisInstruction().get(compare);
        var replaceData = Objects.requireNonNull(pass.thisInstruction().get(replace));
        if(compareData == null || ctx.definitionCount(compareData) > 1 || ctx.definitionCount(replaceData) != 1) return;

        // Any dominating computation yields the same value. Only earlier instructions are considered (a later one
        // can only dominate through a loop), and the earliest usually dominates, so this is O(1) in practice.
        int self = pass.thisInstructionIndex();
        var matches = pass.instructionsWith(type, compare, compareData);
        int best = -1;
        for(int k = 0, size = matches.size(); k < size; k++) {
            int m = matches.getInt(k);
            if(m >= self) break;
            if(pass.dominates(m, self)) {
                best = m;
                break;
            }
        }
        if(best < 0) return;
        var newReplace = Objects.requireNonNull(pass.getInstruction(best).get(replace));
        if(ctx.definitionCount(newReplace) != 1) return;

        pass.replaceInstructionData(false, replaceData, newReplace);
        pass.removeInstruction(self);
    }

    private static void removeDuplicateChecks(OptimizationPass pass) {
        var ctx = pass.context();
        var thisInstr = pass.thisInstruction();
        if(thisInstr.type() == InstructionTypes.IF_NULL_JUMP) {
            // IF_NULL_JUMP(x) is dead when x is known non-null here: an earlier IF_NULL_JUMP(x) must have been passed
            // on its non-null edge. That holds when the earlier check's fall-through instruction dominates this one
            // and can only be entered from that check. Dominance of the earlier check itself is not enough: its
            // null-target usually falls through into the next command (IE-01).
            var local = thisInstr.get("local");
            if(local == null) return;
            int defCount = ctx.definitionCount(local);
            if(defCount > 1) return; // reassigned (e.g. in a loop): the earlier check may be about another value
            int def = defCount == 1 ? ctx.singleDefinitionIndex(local) : -1;
            if(defCount == 1 && def < 0) return;

            // Nearest earlier checks first; the bound keeps functions with thousands of checks on one local linear.
            int self = pass.thisInstructionIndex();
            int n = ctx.getInstructionCount();
            var sameLocalChecks = pass.instructionsWith(InstructionTypes.IF_NULL_JUMP, "local", local);
            int pos = lowerBound(sameLocalChecks, self);
            int tried = 0;
            for(int k = pos - 1; k >= 0 && tried < MAX_DUPLICATE_CHECK_CANDIDATES; k--, tried++) {
                int idx = sameLocalChecks.getInt(k);
                int fall = idx + 1;
                if(fall >= n) continue;
                if(ctx.predecessorCount(fall) != 1) continue;
                if(!pass.dominates(fall, self)) continue;
                // The checked value must be the one we read: the only definition precedes the earlier check.
                if(def >= 0 && !pass.dominates(def, idx)) continue;
                pass.removeInstruction(self);
                return;
            }
        }else if(thisInstr.type() == InstructionTypes.ASSUME_COMMAND_STACK) {
            var stackFrom = Objects.requireNonNull(thisInstr.get("object"));
            var stackTo = Objects.requireNonNull(thisInstr.get("result"));
            if(ctx.definitionCount(stackFrom) != 1 || ctx.definitionCount(stackTo) != 1) return;
            int def = ctx.singleDefinitionIndex(stackFrom);
            if(def >= 0 && pass.getInstruction(def).type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
                pass.replaceInstructionData(false, stackFrom, stackTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }else if(thisInstr.type() == InstructionTypes.ASSUME_ENTITY) {
            var entityFrom = Objects.requireNonNull(thisInstr.get("object"));
            var entityTo = Objects.requireNonNull(thisInstr.get("result"));
            if(ctx.definitionCount(entityFrom) != 1 || ctx.definitionCount(entityTo) != 1) return;
            int def = ctx.singleDefinitionIndex(entityFrom);
            if(def < 0) return;
            var defType = pass.getInstruction(def).type();
            if(defType == InstructionTypes.ASSUME_ENTITY || defType == InstructionTypes.ASSUME_PLAYER) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    /**
     * Removes a list that can hold at most one element (a single-entity selector result):
     * <pre>
     *   CREATE_MUTABLE_LIST L
     *   ...                                    (anything, loops included, e.g. a first-match scan)
     *   ADD_LIST_ELEMENT L, e
     *   [JUMP landing]                         (or fall straight into the landing labels)
     *   ...
     *   LABEL landing ...                      (labels only; entered otherwise by jumps, never by fall-through)
     *   GET_LIST_LENGTH L -> len
     *   IF_NUMBER_COMPARE_JUMP len ==/!= 0     (one or more)
     *   [GET_LIST_ELEMENT L, 0 -> x]
     * </pre>
     * The add leads straight to the length check, and every path from the length check back to itself passes the
     * create. So at the check the list holds exactly {@code e} when it was entered through the add's edge, and is
     * empty otherwise. Every other entry is retargeted to where the compares send an empty list; the add's edge then
     * always sees one element, so the compares fold to their constant outcome and {@code x = e}.
     */
    private static void inlineLists(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes.CREATE_MUTABLE_LIST) return;
        var ctx = pass.context();
        int create = pass.thisInstructionIndex();
        var list = Objects.requireNonNull(pass.thisInstruction().get("result"));
        if(ctx.definitionCount(list) != 1) return;
        var adds = pass.instructionsWith(InstructionTypes.ADD_LIST_ELEMENT, "list", list);
        var lens = pass.instructionsWith(InstructionTypes.GET_LIST_LENGTH, "list", list);
        var gets = pass.instructionsWith(InstructionTypes.GET_LIST_ELEMENT, "list", list);
        if(adds.size() != 1 || lens.size() != 1 || gets.size() > 1) return;
        // Nothing else may see the list (sorting, shuffling, passing it to a command, ...).
        if(ctx.getGlobalValueCounts().getOrDefault(list, 0) != 3 + gets.size()) return;
        int addInstr = adds.getInt(0);
        int lenInstr = lens.getInt(0);
        var len = Objects.requireNonNull(pass.getInstruction(lenInstr).get("result"));
        if(ctx.definitionCount(len) != 1) return;

        // The compares right after the length check. An empty list leaves at the first EQUAL one, a one-element list
        // at the first NOT_EQUAL one (or falls through past all of them).
        var zero = LocationData.ofInteger(0);
        var compares = new IntArrayList();
        @Nullable LabelData emptyTarget = null;
        int oneExit = -1;
        int after = pass.nextInstructionIndex(lenInstr);
        for(; after >= 0; after = pass.nextInstructionIndex(after)) {
            var instr = pass.getInstruction(after);
            if(instr.type() != InstructionTypes.IF_NUMBER_COMPARE_JUMP) break;
            var compare = instr.get("compare");
            if(compare != NumberComparisonType.EQUAL && compare != NumberComparisonType.NOT_EQUAL) break;
            var a = instr.get("a");
            var b = instr.get("b");
            if(!(Objects.equals(a, len) && Objects.equals(b, zero) || Objects.equals(a, zero) && Objects.equals(b, len))) break;
            compares.add(after);
            if(compare == NumberComparisonType.EQUAL && emptyTarget == null) emptyTarget = instr.get("label");
            if(compare == NumberComparisonType.NOT_EQUAL && oneExit < 0) oneExit = after;
        }
        if(compares.isEmpty() || ctx.getGlobalValueCounts().getOrDefault(len, 0) != 1 + compares.size()) return;
        int getInstr = -1;
        if(gets.size() == 1) {
            getInstr = gets.getInt(0);
            // Must be reachable with one element only: right after the compares, an empty list having left.
            if(getInstr != after || emptyTarget == null || oneExit >= 0) return;
            if(!Objects.equals(pass.getInstruction(getInstr).get("index"), zero)) return;
        }

        // Landing labels right before the length check, and what precedes them.
        var landingLabels = new ArrayList<LabelData>();
        int beforeLanding = lenInstr - 1;
        for(; beforeLanding >= 0; beforeLanding--) {
            var instr = pass.getInstruction(beforeLanding);
            if(OptimizationContext.isRemoved(instr)) continue;
            if(instr.type() != InstructionTypes.LABEL) break;
            landingLabels.add(Objects.requireNonNull(instr.<LabelData>get(0)));
        }
        if(beforeLanding < 0) return;
        if(emptyTarget != null && landingLabels.contains(emptyTarget)) return;

        // The add's edge: falling into the landing labels, or an unconditional jump to one of them.
        int addJump = -1;
        if(beforeLanding != addInstr) {
            int next = pass.nextInstructionIndex(addInstr);
            if(next < 0) return;
            var nextInstr = pass.getInstruction(next);
            if(nextInstr.type() != InstructionTypes.JUMP || !landingLabels.contains(nextInstr.<LabelData>get(0))) return;
            addJump = next;
            if(!OptimizationContext.endsBlock(pass.getInstruction(beforeLanding).type())) return;
        }

        // Every other entry is a jump to a landing label.
        var otherEntries = new IntOpenHashSet();
        for(var label : landingLabels) {
            var occurrences = ctx.valueOccurrences(label);
            if(occurrences == null) continue;
            for(int k = 0, size = occurrences.size(); k < size; k++) {
                int idx = occurrences.getInt(k);
                if(idx == addJump) continue;
                var instr = pass.getInstruction(idx);
                if(instr.type() == InstructionTypes.LABEL || !instr.containsValue(label)) continue;
                if(idx == lenInstr || idx == getInstr || compares.contains(idx)) return;
                otherEntries.add(idx);
            }
        }
        if(!otherEntries.isEmpty() && emptyTarget == null) return;

        // At most one add per list lifetime: the list is created on every path to the check and afresh between two
        // visits of it (the add only leads to the check).
        if(!pass.dominates(create, lenInstr) || ctx.reachesItselfAvoiding(lenInstr, create)) return;

        for(var it = otherEntries.intIterator(); it.hasNext(); ) {
            int idx = it.nextInt();
            var data = pass.getInstruction(idx).data();
            @Nullable Object[] newData = data.clone();
            for(int d = 0; d < newData.length; d++) {
                if(newData[d] instanceof LabelData ld && landingLabels.contains(ld)) newData[d] = emptyTarget;
            }
            pass.modifyInstructionData(idx, newData);
        }
        // Only the add's edge reaches the length check now: the list holds exactly the element there.
        if(getInstr >= 0) {
            var element = Objects.requireNonNull(pass.getInstruction(addInstr).get("element"));
            var getResult = Objects.requireNonNull(pass.getInstruction(getInstr).get("result"));
            pass.replaceInstruction(getInstr, Instructions.dup((LocationData<Object>) getResult, (LocationData<Object>) element));
        }
        for(int k = 0, size = compares.size(); k < size; k++) {
            int c = compares.getInt(k);
            if(c == oneExit) pass.replaceInstruction(c, Instructions.jump(Objects.requireNonNull(pass.getInstruction(c).get("label"))));
            else pass.removeInstruction(c); // an EQUAL compare never fires; anything after the exit is unreachable
        }
        pass.removeInstruction(lenInstr);
        pass.removeInstruction(addInstr);
        pass.removeInstruction(create);
    }

    /**
     * Removes IF_NUMBER_COMPARE_JUMP instructions whose operand is a local that no instruction ever writes.
     * Such compares only exist on landing pads reached after an earlier check failed (e.g. chained
     * {@code execute if score ... if score ... run}); the jump can never be taken there.
     */
    private static void removeDeadComparisons(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes.IF_NUMBER_COMPARE_JUMP) return;
        var a = pass.thisInstruction().get("a");
        var b = pass.thisInstruction().get("b");
        if(isUnstoredLocal(pass, a) || isUnstoredLocal(pass, b)) {
            pass.removeInstruction(pass.thisInstructionIndex());
        }
    }

    private static boolean isUnstoredLocal(OptimizationPass pass, @Nullable Object value) {
        if(!(value instanceof LocationData<?> loc) || loc.local() == null) return false;
        var ctx = pass.context();
        int total = ctx.definitionCount(value);
        if(total == 0) return true;
        var self = pass.thisInstruction();
        int selfResultIdx = ctx.resultArgIndex(self.type());
        if(selfResultIdx >= 0 && Objects.equals(self.data()[selfResultIdx], value)) {
            return total == 1;
        }
        return false;
    }

    private static void removeUnusedData(OptimizationPass pass) {
        if(pass.thisInstruction().type().isPure()) {
            var result = Objects.requireNonNull(pass.thisInstruction().get("result"));
            if(!pass.isValueUsedAnywhere(result)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }
}
