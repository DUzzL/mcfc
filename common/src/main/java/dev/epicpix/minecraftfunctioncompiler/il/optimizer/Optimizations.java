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
        Optimizations::removeUnusedData
    );

    private static void mergeLabels(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes.LABEL) return;
        var thisLabel = Objects.requireNonNull(pass.thisInstruction().<LabelData>get("label"));

        var prevInstruction = pass.previousInstruction();
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes.LABEL) {
            var newLabel = Objects.requireNonNull(prevInstruction.<LabelData>get("label"));
            int instrCount = pass.context().getInstructionCount();
            for(int o = 0; o<instrCount; o++) {
                if(o == pass.thisInstructionIndex()) continue;
                var instr = pass.getInstruction(o);
                var replacedInstructionData = instr.optReplaceDataIf(value -> value instanceof LabelData(int id) && id == thisLabel.id(), newLabel);
                if(replacedInstructionData.isPresent()) {
                    pass.modifyInstructionData(o, replacedInstructionData.get());
                }
            }
            pass.removeInstruction(pass.thisInstructionIndex());
            return;
        }

        int useCount = 0;
        for(int i = 0; i<pass.context().getInstructionCount(); i++) {
            if(i == pass.thisInstructionIndex()) continue;
            var instr = pass.getInstruction(i);
            if((instr.type() == InstructionTypes.JUMP || instr.type().isConditionJump()) && instr.containsValue(thisLabel)) {
                useCount++;
            }
        }
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
            var reassignmentInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes.DUP, List.of(Pair.of("result", compareData)));
            if(reassignmentInstrs.isEmpty()) {
                var labelData = pass.thisInstruction().get("label");
                var delInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes.IF_NULL_JUMP, List.of(Pair.of("local", compareData), Pair.of("label", labelData)));
                if(!delInstrs.isEmpty()) {
                    var validDelInstrs = new IntOpenHashSet(pass.findExecutedInstructions());
                    validDelInstrs.retainAll(delInstrs);
                    if(!validDelInstrs.isEmpty()) {
                        pass.removeInstruction(pass.thisInstructionIndex());
                    }
                }
            }
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
