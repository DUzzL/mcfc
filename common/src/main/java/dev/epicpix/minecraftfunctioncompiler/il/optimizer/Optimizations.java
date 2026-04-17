package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Optimizations {
    private Optimizations() {}

    public static final List<Optimization> OPTIMIZATIONS = List.of(
        Optimizations::mergeValues,
        Optimizations::removeDuplicateChecks,
        Optimizations::inlineLists,
        Optimizations::mergeLabels,
        Optimizations::removeUselessCommandStackData,
        Optimizations::removeDeadComparisons,
        Optimizations::removeUnusedData
    );

    /**
     * Reduced optimisation set used for functions that exceed
     * {@link Optimizer#LARGE_FUNCTION_THRESHOLD} instructions.
     * {@code inlineLists} is intentionally omitted: it moves instruction blocks
     * via repeated {@code ArrayList.remove} / {@code add} calls which are O(n)
     * each, making the overall pass O(n * moves) — prohibitive for 10k-50k
     * instruction config functions.  The emitted code is still correct; entity
     * selectors remain in list form rather than being inlined, which has no
     * observable effect for functions that run infrequently (config/init).
     */
    public static final List<Optimization> LARGE_FUNCTION_OPTIMIZATIONS = List.of(
        Optimizations::mergeValues,
        Optimizations::removeDuplicateChecks,
        Optimizations::mergeLabels,
        Optimizations::removeUselessCommandStackData,
        Optimizations::removeDeadComparisons,
        Optimizations::removeUnusedData
    );

    /**
     * One-shot per round: collapse every chain of adjacent LABEL instructions
     * into a single LABEL by rewriting every reference to the doomed labels
     * to point at the surviving (earliest) label, then removing the doomed
     * LABEL instructions.  Total cost O(N × avgDataLen) regardless of how
     * many adjacent pairs exist.
     */
    static void bulkMergeAdjacentLabels(OptimizationPass pass) {
        var ctx = pass.context();
        int n = ctx.getInstructionCount();
        if(n < 2) return;

        // Step 1: build forwarding map keyed by label id.
        java.util.HashMap<Integer, LabelData> forwarding = null;
        LabelData prevLabel = null;
        for(int i = 0; i < n; i++) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL) {
                var ld = instr.<LabelData>get("label");
                if(prevLabel != null) {
                    if(forwarding == null) forwarding = new java.util.HashMap<>();
                    // Resolve transitively: if prevLabel itself was forwarded
                    // (chain of three or more adjacent labels), forward
                    // straight to the chain's root rather than an
                    // intermediate label that's about to be removed.
                    var target = forwarding.getOrDefault(prevLabel.id(), prevLabel);
                    forwarding.put(ld.id(), target);
                }
                prevLabel = ld;
            } else {
                prevLabel = null;
            }
        }
        if(forwarding == null) return;

        // Step 2: single sweep — rewrite references in every instruction.
        // Skip LABEL definitions: rewriting their own "label" slot would
        // change the label they define, breaking step 3's identification of
        // which LABELs to remove and leaving duplicate-id LABELs in the IR.
        final var fwd = forwarding;
        for(int i = 0; i < n; i++) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL) continue;
            var data = instr.data();
            Object[] newData = null;
            for(int d = 0; d < data.length; d++) {
                if(data[d] instanceof LabelData(int id)) {
                    var to = fwd.get(id);
                    if(to != null) {
                        if(newData == null) newData = data.clone();
                        newData[d] = to;
                    }
                }
            }
            if(newData != null) pass.modifyInstructionData(i, newData);
        }

        // Step 3: drop the now-redundant LABEL instructions, walking backward
        // so removals don't disturb indices we haven't visited yet.
        for(int i = ctx.getInstructionCount() - 1; i >= 0; i--) {
            var instr = ctx.getInstruction(i);
            if(instr.type() == InstructionTypes.LABEL) {
                var ld = instr.<LabelData>get("label");
                if(fwd.containsKey(ld.id())) pass.removeInstruction(i);
            }
        }
    }

    private static void mergeLabels(OptimizationPass pass) {
        // Bulk-merge ALL adjacent LABEL pairs in one O(N × avgDataLen) sweep
        // at the start of the round.  The previous per-instruction approach
        // was O(K × N) where K is the number of adjacent-LABEL pairs: every
        // pair triggered a separate scan of all jump instructions to rewrite
        // the doomed label's references.  On a single .mcfunction with
        // ~5 k adjacent pairs and ~50 k jumps that was ~250 M containsValue
        // calls per round and dominated total compile time (observed via
        // thread dump: one carrier thread at 99.5% CPU for 558 s).
        //
        // The bulk algorithm:
        //   1. Walk instructions, build forwarding map: for every adjacent
        //      LABEL X; LABEL Y, record forwarding[Y.id] = X (resolving X's
        //      own forwarding transitively if it is itself a forwarded
        //      target).
        //   2. Single sweep: for every instruction, replace any LabelData
        //      whose id is forwarded with its forwarding target.
        //   3. Walk backward and remove every LABEL whose id is in the
        //      forwarding map.
        if(pass.thisInstructionIndex() == 0) {
            bulkMergeAdjacentLabels(pass);
        }

        if(pass.thisInstruction().type() != InstructionTypes.LABEL) return;
        var thisLabel = Objects.requireNonNull(pass.thisInstruction().<LabelData>get("label"));

        var prevInstruction = pass.previousInstruction();
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes.LABEL) {
            // Defer: another opt (or this opt's own Case B below) created an
            // adjacent LABEL pair AFTER the start-of-round bulk ran.  Doing
            // the per-instruction O(N) typeIndex scan to fix it here turned
            // pathological — every Case A fallback firing rebuilt typeIndex
            // (because other opts' removes invalidated it), and on a single
            // big function this loop burned ~500 s of CPU.  Returning here
            // leaves the adjacent pair in place; the next round's bulk pass
            // will collapse it.  After the final round, optimize() runs one
            // more bulk pass to catch anything still standing.
            return;
        }

        // Fast path: globalValueCounts is an incrementally maintained O(1)
        // lookup of "how many times does this exact Object appear across all
        // instruction data[] slots?". LabelData is only ever stored in two
        // places — the LABEL instruction's own "label" slot (exactly once)
        // and the target slot of a JUMP / conditional jump — so total count
        // minus the self-reference is precisely the jump-target use count.
        // Previously this was an O(N) scan, making the whole pass O(N²) on
        // label-heavy functions: a single 18k-command .mcfunction with ~10k
        // labels was burning ~500 M Objects.equals calls per pass round.
        int useCount = pass.context().getGlobalValueCounts().getOrDefault(thisLabel, 0) - 1;
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes.JUMP && Objects.equals(prevInstruction.get("label"), thisLabel)) {
            if(useCount == 1) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
            pass.removeInstruction(pass.thisInstructionIndex() - 1);
        }else if(useCount == 0) {
            pass.removeInstruction(pass.thisInstructionIndex());
        }

    }

    private static void removeUselessCommandStackData(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes.GET_STACK_ENTITY) {
            var stack = pass.thisInstruction().get("stack");
            Object entitySource = null;
            for(int i = 0; i<pass.context().getInstructionCount(); i++) {
                var instr = pass.getInstruction(i);
                if(instr.type() == InstructionTypes.GET_COMMAND_STACK) {
                    if(Objects.equals(instr.get("result"), stack)) {
                        return;
                    }
                }else if(instr.type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
                    if(Objects.equals(instr.get("result"), stack)) {
                        entitySource = instr.get("entity");
                        break;
                    }
                }
            }
            if(entitySource == null) return;

            var replaceFrom = Objects.requireNonNull(pass.thisInstruction().get("result"));
            pass.replaceInstructionData(false, replaceFrom, entitySource);
            pass.removeInstruction(pass.thisInstructionIndex());

        }else if(pass.thisInstruction().type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
            if(pass.thisInstruction().get("anchor") == null && pass.thisInstruction().get("entity") == null && pass.thisInstruction().get("position") == null && pass.thisInstruction().get("rotation") == null && pass.thisInstruction().get("world") == null) {
                pass.replaceInstructionData(false, Objects.requireNonNull(pass.thisInstruction().get("result")), Objects.requireNonNull(pass.thisInstruction().get("from")));
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void mergeValues(OptimizationPass pass) {
        mergePureData(pass, InstructionTypes.GET_STACK_ENTITY, "stack", "result");
        mergePureData(pass, InstructionTypes.GET_SCOREBOARD_NAME, "entity", "result");

        if(pass.thisInstruction().type() == InstructionTypes2.CALL_FUNCTIONS) {
            var prev = pass.previousInstruction();
            if(prev != null && prev.type() == InstructionTypes2.CALL_FUNCTIONS) {
                if(Objects.equals(pass.thisInstruction().get("arguments"), prev.get("arguments"))) {
                    if(Objects.equals(pass.thisInstruction().get("stack"), prev.get("stack"))) {
                        var functions = new ArrayList<DataLocation>(Objects.requireNonNull(prev.get("functions")));
                        functions.addAll(pass.thisInstruction().get("functions"));
                        pass.replaceInstruction(pass.thisInstructionIndex() - 1, Instructions2.callFunctions(functions, prev.get("stack"), prev.get("arguments")));
                        pass.removeInstruction(pass.thisInstructionIndex());
                    }
                }
            }
        }else if(pass.thisInstruction().type() == InstructionTypes.MERGE_POSITIONS) {
            if(pass.thisInstruction().get("x") != null && pass.thisInstruction().get("y") != null && pass.thisInstruction().get("z") != null) {
                pass.replaceInstruction(pass.thisInstructionIndex(), Instructions.createPosition(pass.thisInstruction().get("result"), pass.thisInstruction().get("x"), pass.thisInstruction().get("y"), pass.thisInstruction().get("z")));
            }
        }
    }

    private static void mergePureData(OptimizationPass pass, InstructionType type, String compare, String replace) {
        if(pass.thisInstruction().type() == type) {
            var compareData = pass.thisInstruction().get(compare);
            var replaceData = Objects.requireNonNull(pass.thisInstruction().get(replace));
            var validReplMatchesInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, type, List.of(Pair.of(compare, compareData)));
            if(!validReplMatchesInstrs.isEmpty()) {
                var validReplInstrs = new IntArrayList(pass.findExecutedInstructions());
                validReplInstrs.retainAll(validReplMatchesInstrs);
                if(!validReplInstrs.isEmpty()) {
                    var newReplace = Objects.requireNonNull(pass.getInstruction(validReplInstrs.getInt(0)).get(replace));

                    pass.replaceInstructionData(false, replaceData, newReplace);
                    pass.removeInstruction(pass.thisInstructionIndex());
                }
            }
        }
    }

    private static void removeDuplicateChecks(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes.IF_NULL_JUMP) {
            var compareData = pass.thisInstruction().get("local");
            int n = pass.context().getInstructionCount();

            // Find ALL prior IF_NULL_JUMP instructions checking this same local,
            // regardless of their target label. The key insight: if a previous
            // IF_NULL_JUMP on the same local DOMINATES this one and the local is
            // not re-assigned between them, then by the time control flow reaches
            // this instruction the local is provably non-null — so this jump can
            // never branch and is dead code. The previous version only matched
            // duplicates with the *exact same* target label, which missed the
            // common pattern of `execute if score ... if score ... if score`
            // where each segment generates its own selector check with a
            // different skip label, leaving 4+ identical IF_NULL_JUMPs that the
            // optimizer couldn't fold (e.g. armor_statues:locking/uuid_check).
            var sameLocalChecks = pass.findInstructions(0, n, false,
                InstructionTypes.IF_NULL_JUMP,
                List.of(Pair.of("local", compareData)));
            if(sameLocalChecks.isEmpty()) return;

            var dominators = new IntOpenHashSet(pass.findExecutedInstructions());

            // Pick the closest dominating predecessor — fewer instructions
            // between it and us means smaller window for re-assignment.
            int closestPrev = -1;
            int thisIdx = pass.thisInstructionIndex();
            for(var it = sameLocalChecks.intIterator(); it.hasNext(); ) {
                int idx = it.nextInt();
                if(idx < thisIdx && idx > closestPrev && dominators.contains(idx)) {
                    closestPrev = idx;
                }
            }
            if(closestPrev == -1) return;

            // Verify no instruction strictly between closestPrev and us writes
            // the local through its "result" field. We scan all argument names
            // by string match because "result" is not always at index 0
            // (e.g. SCORE_DATA_NO_AUTO_CREATE places it after holder/objective).
            for(int i = closestPrev + 1; i < thisIdx; i++) {
                var instr = pass.getInstruction(i);
                String[] names = instr.type().argumentNames();
                Object[] data = instr.data();
                for(int j = 0; j < names.length; j++) {
                    if("result".equals(names[j]) && Objects.equals(data[j], compareData)) {
                        return; // re-assignment between checks — keep this one
                    }
                }
            }

            // Provably dead jump — remove it.
            pass.removeInstruction(thisIdx);
        }else if(pass.thisInstruction().type() == InstructionTypes.ASSUME_COMMAND_STACK) {
            var stackFrom = Objects.requireNonNull(pass.thisInstruction().get("object"));
            var stackTo = Objects.requireNonNull(pass.thisInstruction().get("result"));

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.MAKE_COMMAND_SOURCE_STACK, List.of(Pair.of("result", stackFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, stackFrom, stackTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }else if(pass.thisInstruction().type() == InstructionTypes.ASSUME_ENTITY) {
            var entityFrom = Objects.requireNonNull(pass.thisInstruction().get("object"));
            var entityTo = Objects.requireNonNull(pass.thisInstruction().get("result"));

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.ASSUME_ENTITY, List.of(Pair.of("result", entityFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
                return;
            }

            var def2 = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.ASSUME_PLAYER, List.of(Pair.of("result", entityFrom)));
            if(def2 != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void inlineLists(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes.CREATE_MUTABLE_LIST) return;

        var list = pass.thisInstruction().<LocationData<List<Object>>>get("result");

        var addInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.ADD_LIST_ELEMENT, List.of(Pair.of("list", list)));
        if(addInstr == null) return;

        var listLengthInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.GET_LIST_LENGTH, List.of(Pair.of("list", list)));
        if(listLengthInstr == null) return;

        var getInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.GET_LIST_ELEMENT, List.of(Pair.of("list", list)));
        if(getInstr == null) return;

        var condInstr = getInstr - 1;
        if(!pass.getInstruction(condInstr).containsValue(Objects.requireNonNull(pass.getInstruction(listLengthInstr).get("result")))) return;
        var condInstrType = pass.getInstruction(condInstr).type();
        boolean singleSelector;
        LabelData endLabel;
        if(condInstrType == InstructionTypes.IF_NUMBER_COMPARE_JUMP) {
            var condition = pass.getInstruction(condInstr).<NumberComparisonType>get("compare");
            if(condition == NumberComparisonType.EQUAL) {
                if(!pass.getInstruction(condInstr).containsValue(LocationData.ofInteger(0))) return;
                singleSelector = true;
                endLabel = pass.getInstruction(condInstr).get("label");
            }else if(condition == NumberComparisonType.GREATER_THAN_OR_EQUAL) {
                singleSelector = false;
                endLabel = pass.getInstruction(condInstr).get("label");
            }else return;
        }else return;

        var endInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.LABEL, List.of(Pair.of("label", endLabel)));
        if(endInstr == null) return;

        if(!pass.findInstructions(0, pass.context().getInstructionCount(), false, List.of(InstructionTypes.SORT_LIST_ENTITY_FURTHEST, InstructionTypes.SORT_LIST_ENTITY_NEAREST, InstructionTypes.SHUFFLE_LIST), List.of(Pair.of("list", list))).isEmpty()) return;

        if(!singleSelector) {
            if(listLengthInstr + 1 < pass.context().getInstructionCount()) return;
            var sizeCheckInstr = pass.getInstruction(listLengthInstr + 1);
            if(sizeCheckInstr.type() == InstructionTypes.IF_NUMBER_COMPARE_JUMP && sizeCheckInstr.containsValue(Objects.requireNonNull(pass.getInstruction(listLengthInstr).get("result"))) && sizeCheckInstr.containsValue(LocationData.ofInteger(0))) return;

            if(!pass.findInstructions(condInstr, endInstr, false, List.of(InstructionTypes2.CALL_FUNCTIONS, InstructionTypes.KILL_ENTITY), List.of()).isEmpty()) return;

            var indexLoc = Objects.requireNonNull(pass.getInstruction(getInstr).get("index"));

            var indexDefInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.DUP, List.of(Pair.of("result", indexLoc), Pair.of("src", LocationData.ofInteger(0))));
            if(indexDefInstr == null) return;

            var adderInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.VALUE_ADD, List.of(Pair.of("result", indexLoc), Pair.of("a", indexLoc), Pair.of("b", LocationData.ofInteger(1))));
            if(adderInstr == null) return;

            var jumpInstr = adderInstr + 1;
            if(pass.getInstruction(jumpInstr).type() != InstructionTypes.JUMP) return;

            for(int i = 0; i<pass.context().getInstructionCount(); i++) {
                if(i == getInstr || i == condInstr || i == indexDefInstr || i == adderInstr) continue;
                var instr = pass.getInstruction(i);
                if(instr.containsValue(indexLoc)) {
                    return;
                }
            }


            pass.replaceInstruction(getInstr, Instructions.dup(pass.getInstruction(getInstr).get("result"), pass.getInstruction(addInstr).get("element")));
            pass.removeInstruction(jumpInstr);
            pass.removeInstruction(adderInstr);
            pass.removeInstruction(condInstr);
            pass.removeInstruction(indexDefInstr);
            pass.removeInstruction(listLengthInstr);
            pass.removeInstruction(addInstr);
            pass.removeInstruction(pass.thisInstructionIndex());
            var moveSelStart = condInstr - 5;
            var moveSelEnd = endInstr - 8;
            var instrs = new ArrayList<Instruction>();
            for(int i = moveSelStart; i <= moveSelEnd; i++) {
                var r = pass.getInstruction(moveSelStart);
                pass.removeInstruction(moveSelStart);
                instrs.add(r);
            }
            for(int i = instrs.size() - 1; i >= 0; i--) {
                Instruction r = instrs.get(i);
                pass.addInstruction(addInstr - 1, r);
            }
        }else {
            pass.replaceInstruction(getInstr, Instructions.dup(pass.getInstruction(getInstr).get("result"), pass.getInstruction(addInstr).get("element")));
            pass.removeInstruction(condInstr);
            pass.removeInstruction(listLengthInstr);
            pass.removeInstruction(addInstr);
            pass.removeInstruction(pass.thisInstructionIndex());
            var moveSelStart = condInstr - 3;
            var moveSelEnd = endInstr - 5;
            var instrs = new ArrayList<Instruction>();
            for(int i = moveSelStart; i <= moveSelEnd; i++) {
                var r = pass.getInstruction(moveSelStart);
                pass.removeInstruction(moveSelStart);
                instrs.add(r);
            }
            instrs.add(Instructions.jump(endLabel));
            for(int i = instrs.size() - 1; i >= 0; i--) {
                Instruction r = instrs.get(i);
                pass.addInstruction(addInstr - 1, r);
            }
        }

    }

    /**
     * Removes IF_NUMBER_COMPARE_JUMP instructions whose operand is a local
     * that is never written by any instruction in the function.
     *
     * Background: when control flow falls into a landing pad after a
     * `IF_NULL_JUMP local<X> → label` because local<X> was null, code in the
     * landing pad may read other locals (e.g. score values) that were only
     * defined in the happy path that we skipped. The emitter falls back to
     * NullValue for such loads and prints a "Loading undefined local" warning.
     *
     * For numeric comparisons against a constant the result is always false
     * (null != any int), so the jump is unreachable and the whole instruction
     * can be deleted. After deletion the landing pad collapses to an empty
     * label which mergeLabels then folds into the following block.
     *
     * This is the second half of the fix for the chained
     * `execute if score ... if score ... run function` pattern, with
     * removeDuplicateChecks handling the IF_NULL_JUMP side. See e.g.
     * armor_statues:locking/uuid_check, armor_statues:help/chat_actionbar_toggle.
     */
    private static void removeDeadComparisons(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes.IF_NUMBER_COMPARE_JUMP) return;
        var a = pass.thisInstruction().get("a");
        var b = pass.thisInstruction().get("b");
        if(isUnstoredLocal(pass, a) || isUnstoredLocal(pass, b)) {
            pass.removeInstruction(pass.thisInstructionIndex());
        }
    }

    /**
     * Returns true iff {@code value} is a local-flavoured LocationData that
     * is never written through any instruction's "result" field anywhere in
     * the function (the current instruction itself is excluded — a value is
     * "unstored" if NO other instruction writes it).
     *
     * <p>O(1) via the incrementally-maintained result-value count map in
     * {@link OptimizationContext}.  Was an O(n) full-instruction scan; called
     * twice per IF_NUMBER_COMPARE_JUMP from removeDeadComparisons, that loop
     * was O(n²) and burned ~744 s of CPU on a single 18 k-command function.
     */
    private static boolean isUnstoredLocal(OptimizationPass pass, Object value) {
        if(!(value instanceof LocationData<?> loc) || loc.local() == null) return false;
        var ctx = pass.context();
        int total = ctx.getResultValueCounts().getOrDefault(value, 0);
        if(total == 0) return true;
        // Total > 0: at least one instruction has this value as its result.
        // Subtract the current instruction's contribution if it stores too.
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
            // O(1) via cached global-value-frequency map instead of O(n) linear scan
            if(!pass.isValueUsedAnywhere(result)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }


}
