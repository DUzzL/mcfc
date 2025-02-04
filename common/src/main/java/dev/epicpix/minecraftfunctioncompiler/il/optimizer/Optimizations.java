package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.*;

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

    private static void mergeLabels(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes2.LABEL) return;
        var thisLabel = pass.thisInstruction().<LabelData>get("label");

        var prevInstruction = pass.getOffsetInstruction(-1);
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes2.LABEL) {
            var newLabel = prevInstruction.<LabelData>get("label");
            int instrCount = pass.context().getInstructionCount();
            for(int o = 0; o<instrCount; o++) {
                if(o == pass.thisInstructionIndex()) continue;
                var instr = pass.getInstruction(o);
                Object[] replacedInstructionData = instr.optReplaceDataIf(value -> value instanceof LabelData label && label.id() == thisLabel.id(), newLabel);
                if(replacedInstructionData != null) {
                    pass.modifyInstructionData(o, replacedInstructionData);
                }
            }
            pass.removeInstruction(pass.thisInstructionIndex());
            return;
        }

        int useCount = 0;
        for(int i = 0; i<pass.context().getInstructionCount(); i++) {
            if(i == pass.thisInstructionIndex()) continue;
            var instr = pass.getInstruction(i);
            if((instr.type() == InstructionTypes2.JUMP || instr.type().isConditionJump()) && instr.containsValue(thisLabel)) {
                useCount++;
            }
        }
        if(prevInstruction != null && prevInstruction.type() == InstructionTypes2.JUMP && prevInstruction.get("label").equals(thisLabel)) {
            if(useCount == 1) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
            pass.removeInstruction(pass.thisInstructionIndex() - 1);
        }else if(useCount == 0) {
            pass.removeInstruction(pass.thisInstructionIndex());
        }

    }

    private static void removeUselessCommandStackData(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes2.GET_STACK_ENTITY) {
            var stack = pass.thisInstruction().get("stack");
            Object entitySource = null;
            for(int i = 0; i<pass.context().getInstructionCount(); i++) {
                var instr = pass.getInstruction(i);
                if(instr.type() == InstructionTypes2.GET_COMMAND_STACK) {
                    if(instr.get("result").equals(stack)) {
                        return;
                    }
                }else if(instr.type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
                    if(instr.get("result").equals(stack)) {
                        entitySource = instr.get("entity");
                        break;
                    }
                }
            }
            if(entitySource == null) return;

            var replaceFrom = pass.thisInstruction().get("result");
            pass.replaceInstructionData(false, replaceFrom, entitySource);
            pass.removeInstruction(pass.thisInstructionIndex());

        }else if(pass.thisInstruction().type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
            if(pass.thisInstruction().get("anchor") == null && pass.thisInstruction().get("entity") == null && pass.thisInstruction().get("position") == null && pass.thisInstruction().get("rotation") == null && pass.thisInstruction().get("world") == null) {
                pass.replaceInstructionData(false, pass.thisInstruction().get("result"), pass.thisInstruction().get("from"));
                pass.removeInstruction(pass.thisInstructionIndex());
                return;
            }

            var stack = pass.thisInstruction().get("result");

            if(!pass.isValueUsed(0, pass.context().getInstructionCount(), false, stack)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void mergeValues(OptimizationPass pass) {
        mergePureData(pass, InstructionTypes2.GET_STACK_ENTITY, "stack", "result");
        mergePureData(pass, InstructionTypes2.GET_SCOREBOARD_NAME, "entity", "result");

        if(pass.thisInstruction().type() == InstructionTypes2.OBJECT_ASSIGN) {
            var assignFrom = pass.thisInstruction().get("src");
            var assignTo = pass.thisInstruction().get("result");
            var otherAssigns = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.OBJECT_ASSIGN, List.of(Pair.of("result", assignFrom)));
            if(!otherAssigns.isEmpty()) return;

            pass.replaceInstructionData(false, assignFrom, assignTo);
            pass.removeInstruction(pass.thisInstructionIndex());
        }else if(pass.thisInstruction().type() == InstructionTypes.CALL_FUNCTIONS) {
            var prev = pass.getOffsetInstruction(-1);
            if(prev != null && prev.type() == InstructionTypes.CALL_FUNCTIONS) {
                if(pass.thisInstruction().get("result") == null && prev.get("result") == null) {
                    if(Objects.equals(pass.thisInstruction().get("arguments"), prev.get("arguments"))) {
                        if(Objects.equals(pass.thisInstruction().get("stack"), prev.get("stack"))) {
                            var functions = new ArrayList<DataLocation>(prev.get("functions"));
                            functions.addAll(pass.thisInstruction().get("functions"));
                            pass.replaceInstruction(pass.thisInstructionIndex() - 1, Instructions.callFunctions(null, functions, prev.get("stack"), prev.get("arguments")));
                            pass.removeInstruction(pass.thisInstructionIndex());
                        }
                    }
                }
            }
        }
    }

    private static void mergePureData(OptimizationPass pass, InstructionType type, String compare, String replace) {
        if(pass.thisInstruction().type() == type) {
            var compareData = pass.thisInstruction().get(compare);
            var replaceData = pass.thisInstruction().get(replace);
            var validReplMatchesInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, type, List.of(Pair.of(compare, compareData)));
            if(!validReplMatchesInstrs.isEmpty()) {
                var validReplInstrs = new IntArrayList(pass.findExecutedInstructions());
                validReplInstrs.retainAll(validReplMatchesInstrs);
                if(!validReplInstrs.isEmpty()) {
                    var newReplace = pass.getInstruction(validReplInstrs.getInt(0)).get(replace);

                    pass.replaceInstructionData(false, replaceData, newReplace);
                    pass.removeInstruction(pass.thisInstructionIndex());
                }
            }
        }
    }

    private static void removeDuplicateChecks(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes2.IF_NULL_JUMP) {
            var compareData = pass.thisInstruction().get("local");
            var reassignmentInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.OBJECT_ASSIGN, List.of(Pair.of("result", compareData)));
            if(reassignmentInstrs.isEmpty()) {
                var labelData = pass.thisInstruction().get("label");
                var delInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.IF_NULL_JUMP, List.of(Pair.of("local", compareData), Pair.of("label", labelData)));
                if(!delInstrs.isEmpty()) {
                    var validDelInstrs = new IntOpenHashSet(pass.findExecutedInstructions());
                    validDelInstrs.retainAll(delInstrs);
                    if(!validDelInstrs.isEmpty()) {
                        pass.removeInstruction(pass.thisInstructionIndex());
                    }
                }
            }
        }else if(pass.thisInstruction().type() == InstructionTypes2.ASSUME_COMMAND_STACK) {
            var stackFrom = pass.thisInstruction().get("object");
            var stackTo = pass.thisInstruction().get("result");

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.MAKE_COMMAND_SOURCE_STACK, List.of(Pair.of("result", stackFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, stackFrom, stackTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }else if(pass.thisInstruction().type() == InstructionTypes2.ASSUME_ENTITY) {
            var entityFrom = pass.thisInstruction().get("object");
            var entityTo = pass.thisInstruction().get("result");

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ASSUME_ENTITY, List.of(Pair.of("result", entityFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
                return;
            }

            var def2 = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ASSUME_PLAYER, List.of(Pair.of("result", entityFrom)));
            if(def2 != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void inlineLists(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes2.CREATE_MUTABLE_LIST) return;

        var list = pass.thisInstruction().<LocationData<List<Object>>>get("result");

        var addInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ADD_LIST_ELEMENT, List.of(Pair.of("list", list)));
        if(addInstr == null) return;

        var listLengthInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.GET_LIST_LENGTH, List.of(Pair.of("list", list)));
        if(listLengthInstr == null) return;

        var getInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.GET_LIST_ELEMENT, List.of(Pair.of("list", list)));
        if(getInstr == null) return;

        var condInstr = getInstr - 1;
        if(!pass.getInstruction(condInstr).containsValue(pass.getInstruction(listLengthInstr).get("result"))) return;
        var condInstrType = pass.getInstruction(condInstr).type();
        boolean singleSelector;
        LabelData endLabel;
        if(condInstrType == InstructionTypes2.IF_EQUAL_JUMP) {
            if(!pass.getInstruction(condInstr).containsValue(LocationData.ofInteger(0))) return;
            singleSelector = true;
            endLabel = pass.getInstruction(condInstr).get("label");
        }else if(condInstrType == InstructionTypes2.IF_GREATER_EQUAL_JUMP) {
            singleSelector = false;
            endLabel = pass.getInstruction(condInstr).get("label");
        }else {
            return;
        }

        var endInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.LABEL, List.of(Pair.of("label", endLabel)));
        if(endInstr == null) return;

        if(!pass.findInstructions(0, pass.context().getInstructionCount(), false, List.of(InstructionTypes2.SORT_LIST_ENTITY_FURTHEST, InstructionTypes2.SORT_LIST_ENTITY_NEAREST, InstructionTypes2.SHUFFLE_LIST), List.of(Pair.of("list", list))).isEmpty()) return;

        if(!singleSelector) {
            if(!pass.findInstructions(condInstr, endInstr, false, List.of(InstructionTypes.CALL_FUNCTIONS, InstructionTypes2.KILL_ENTITY), List.of()).isEmpty()) return;

            var indexLoc = pass.getInstruction(getInstr).get("index");

            var indexDefInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.VALUE_ASSIGN, List.of(Pair.of("result", indexLoc), Pair.of("src", LocationData.ofInteger(0))));
            if(indexDefInstr == null) return;

            var adderInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.VALUE_ADD, List.of(Pair.of("result", indexLoc), Pair.of("a", indexLoc), Pair.of("b", LocationData.ofInteger(1))));
            if(adderInstr == null) return;

            var jumpInstr = adderInstr + 1;
            if(pass.getInstruction(jumpInstr).type() != InstructionTypes2.JUMP) return;

            for(int i = 0; i<pass.context().getInstructionCount(); i++) {
                if(i == getInstr || i == condInstr || i == indexDefInstr || i == adderInstr) continue;
                var instr = pass.getInstruction(i);
                if(instr.containsValue(indexLoc)) {
                    return;
                }
            }


            pass.replaceInstruction(getInstr, Instructions2.objectAssign(pass.getInstruction(getInstr).get("result"), pass.getInstruction(addInstr).get("element")));
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
            pass.replaceInstruction(getInstr, Instructions2.objectAssign(pass.getInstruction(getInstr).get("result"), pass.getInstruction(addInstr).get("element")));
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
            instrs.add(Instructions2.jump(endLabel));
            for(int i = instrs.size() - 1; i >= 0; i--) {
                Instruction r = instrs.get(i);
                pass.addInstruction(addInstr - 1, r);
            }
        }

    }

    private static void removeUnusedData(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes2.GET_STACK_POSITION) {
            var result = pass.thisInstruction().get("result");

            if(!pass.isValueUsed(0, pass.context().getInstructionCount(), false, result)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }


}
