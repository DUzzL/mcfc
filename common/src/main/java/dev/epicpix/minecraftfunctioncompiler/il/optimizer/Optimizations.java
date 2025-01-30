package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.util.Pair;

import java.util.ArrayList;
import java.util.List;

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
            if(instr.containsValue(thisLabel)) {
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
                    if(instr.get("stack").equals(stack)) {
                        return;
                    }
                }else if(instr.type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
                    if(instr.get("to").equals(stack)) {
                        entitySource = instr.get("entity");
                        break;
                    }
                }
            }
            if(entitySource == null) return;

            var replaceFrom = pass.thisInstruction().get("entity");
            pass.replaceInstructionData(false, replaceFrom, entitySource);
            pass.removeInstruction(pass.thisInstructionIndex());

        }else if(pass.thisInstruction().type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
            var stack = pass.thisInstruction().get("to");

            if(!pass.isValueUsed(0, pass.context().getInstructionCount(), false, stack)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void mergeValues(OptimizationPass pass) {
        mergePureData(pass, InstructionTypes2.GET_STACK_ENTITY, "stack", "entity");
        mergePureData(pass, InstructionTypes2.GET_SCOREBOARD_NAME, "entity", "name");

        if(pass.thisInstruction().type() == InstructionTypes2.OBJECT_ASSIGN) {
            var assignFrom = pass.thisInstruction().get("src");
            var assignTo = pass.thisInstruction().get("dst");
            var otherAssigns = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.OBJECT_ASSIGN, List.of(new Pair<>("dst", assignFrom)));
            if(!otherAssigns.isEmpty()) return;

            pass.replaceInstructionData(false, assignFrom, assignTo);
            pass.removeInstruction(pass.thisInstructionIndex());
        }
    }

    private static void mergePureData(OptimizationPass pass, InstructionType type, String compare, String replace) {
        if(pass.thisInstruction().type() == type) {
            var compareData = pass.thisInstruction().get(compare);
            var replaceData = pass.thisInstruction().get(replace);
            var validReplMatchesInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, type, List.of(new Pair<>(compare, compareData)));
            if(!validReplMatchesInstrs.isEmpty()) {
                var validReplInstrs = new ArrayList<>(pass.findExecutedInstructions());
                validReplInstrs.retainAll(validReplMatchesInstrs);
                if(!validReplInstrs.isEmpty()) {
                    var newReplace = pass.getInstruction(validReplInstrs.get(0)).get(replace);

                    pass.replaceInstructionData(false, replaceData, newReplace);
                    pass.removeInstruction(pass.thisInstructionIndex());
                }
            }
        }
    }

    private static void removeDuplicateChecks(OptimizationPass pass) {
        if(pass.thisInstruction().type() == InstructionTypes2.IF_NULL_JUMP) {
            var compareData = pass.thisInstruction().get("local");
            var reassignmentInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.OBJECT_ASSIGN, List.of(new Pair<>("dst", compareData)));
            if(reassignmentInstrs.isEmpty()) {
                var labelData = pass.thisInstruction().get("label");
                var delInstrs = pass.findInstructions(0, pass.context().getInstructionCount(), false, InstructionTypes2.IF_NULL_JUMP, List.of(new Pair<>("local", compareData), new Pair<>("label", labelData)));
                if(!delInstrs.isEmpty()) {
                    var validDelInstrs = new ArrayList<>(pass.findExecutedInstructions());
                    validDelInstrs.retainAll(delInstrs);
                    if(!validDelInstrs.isEmpty()) {
                        pass.removeInstruction(pass.thisInstructionIndex());
                    }
                }
            }
        }else if(pass.thisInstruction().type() == InstructionTypes2.ASSUME_COMMAND_STACK) {
            var stackFrom = pass.thisInstruction().get("stack_obj");
            var stackTo = pass.thisInstruction().get("stack");

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes.MAKE_COMMAND_SOURCE_STACK, List.of(new Pair<>("to", stackFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, stackFrom, stackTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }else if(pass.thisInstruction().type() == InstructionTypes2.ASSUME_ENTITY) {
            var entityFrom = pass.thisInstruction().get("entity_obj");
            var entityTo = pass.thisInstruction().get("entity");

            var def = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ASSUME_ENTITY, List.of(new Pair<>("entity", entityFrom)));
            if(def != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
                return;
            }

            var def2 = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ASSUME_PLAYER, List.of(new Pair<>("player", entityFrom)));
            if(def2 != null) {
                pass.replaceInstructionData(false, entityFrom, entityTo);
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }

    private static void inlineLists(OptimizationPass pass) {
        if(pass.thisInstruction().type() != InstructionTypes2.CREATE_MUTABLE_LIST) return;

        var list = pass.thisInstruction().<LocationData<List<Object>>>get("list");

        var addInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.ADD_LIST_ELEMENT, List.of(new Pair<>("list", list)));
        if(addInstr == null) return;

        var listLengthInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.GET_LIST_LENGTH, List.of(new Pair<>("list", list)));
        if(listLengthInstr == null) return;

        var getInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.GET_LIST_ELEMENT, List.of(new Pair<>("list", list)));
        if(getInstr == null) return;

        var condInstr = getInstr - 1;
        if(!pass.getInstruction(condInstr).containsValue(pass.getInstruction(listLengthInstr).get("size"))) return;
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

        var endInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.LABEL, List.of(new Pair<>("label", endLabel)));
        if(endInstr == null) return;

        if(!pass.findInstructions(0, pass.context().getInstructionCount(), false, List.of(InstructionTypes2.SORT_LIST_ENTITY_FURTHEST, InstructionTypes2.SORT_LIST_ENTITY_NEAREST, InstructionTypes2.SHUFFLE_LIST), List.of(new Pair<>("list", list))).isEmpty()) return;

        if(!singleSelector) {
            if(!pass.findInstructions(condInstr, endInstr, false, List.of(InstructionTypes.CALL_FUNCTIONS, InstructionTypes2.KILL_ENTITY), List.of()).isEmpty()) return;

            var indexLoc = pass.getInstruction(getInstr).get("index");

            var indexDefInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.VALUE_DEFINE, List.of(new Pair<>("dst", indexLoc), new Pair<>("src", LocationData.ofInteger(0))));
            if(indexDefInstr == null) return;

            var adderInstr = pass.findSingleInstruction(0, pass.context().getInstructionCount(), false, InstructionTypes2.VALUE_ADD, List.of(new Pair<>("a", indexLoc), new Pair<>("b", LocationData.ofInteger(1))));
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


            pass.replaceInstruction(getInstr, Instructions2.objectAssign(pass.getInstruction(getInstr).get("element"), pass.getInstruction(addInstr).get("element")));
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
            pass.replaceInstruction(getInstr, Instructions2.objectAssign(pass.getInstruction(getInstr).get("element"), pass.getInstruction(addInstr).get("element")));
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
            var result = pass.thisInstruction().get("position");

            if(!pass.isValueUsed(0, pass.context().getInstructionCount(), false, result)) {
                pass.removeInstruction(pass.thisInstructionIndex());
            }
        }
    }


}
