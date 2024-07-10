package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationInput.addPathIndex;

public final class Optimizations {
    private Optimizations() {}

    static final Optimization[] OPTIMIZATIONS = {
            Optimizations::removeExitScopeDeadCode,
            Optimizations::inlineListAccess,
            Optimizations::removeUnnecessaryInstructions,
    };

    private static OptimizationResult removeUnnecessaryInstructions(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.ALWAYS) return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
        if(instr.type() == InstructionTypes2.NEVER) return OptimizationResultTypes.REMOVE_SELF;
        if(instr.type() == InstructionTypes2.SCOPE) {
            var scope = instr.<ScopeData>get("scope");
            if(!input.anyChildren(it -> it.data().containsValue(scope))) return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult removeExitScopeDeadCode(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.SCOPE) {
            var scope = instr.<ScopeData>get("scope");
            var scopeInstructions = input.collectChildren(it -> it.instruction().data().containsValue(scope));

            ArrayList<InstructionModification> modifications = new ArrayList<>();
            for(var scopeInstr : scopeInstructions) {
                if(scopeInstr.instruction().type() == InstructionTypes2.EXIT_SCOPE) {
                    var before = input.getByPath(scopeInstr.path().subList(0, scopeInstr.path().size() - 1));
                    for(int i = scopeInstr.path().get(scopeInstr.path().size() - 1) + 1; i<before.instruction().children().size(); i++) {
                        modifications.add(new InstructionModification.RemoveInstruction(addPathIndex(before.path(), i)));
                    }
                }
            }
            if(!modifications.isEmpty()) {
                return new OptimizationResult(null, modifications, OptimizationResult.MODIFY_CHILDREN);
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult inlineListAccess(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.CREATE_MUTABLE_LIST) {
            var listLocation = instr.<LocationData<List<?>>>get("list");
            var addInstructions = input.collectChildren(it -> it.instruction().type() == InstructionTypes2.ADD_LIST_ELEMENT && it.instruction().data().containsValue(listLocation));
            if(addInstructions.size() == 1) {
                var addInstruction = addInstructions.get(0);
                var elementSourceInstruction = input.getInstructionDefinitionInPath(addInstruction.path(), addInstruction.instruction().get("element"));
                var sourceInstructionType = elementSourceInstruction.instruction().type();
                var isEntity = sourceInstructionType == InstructionTypes2.GET_CURRENT_ENTITY || sourceInstructionType == InstructionTypes2.FOR_EACH_ENTITY || sourceInstructionType == InstructionTypes2.FOR_EACH_PLAYER || sourceInstructionType == InstructionTypes2.FOR_ENTITY || sourceInstructionType == InstructionTypes2.FOR_PLAYER;
                if(isEntity) {
                    var otherMutations = input.anyChildren(it -> it.type() != InstructionTypes2.ADD_LIST_ELEMENT && it.type() != InstructionTypes2.FOR_EACH_LIST_ELEMENT && it.data().containsValue(listLocation));
                    if(!otherMutations) {
                        var forEachInstruction = input.getChild(it -> it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT && it.instruction().data().containsValue(listLocation));
                        if(forEachInstruction != null) {
                            var forEachDirectChild = input.getDirectChild(forEachInstruction);
                            var isSafeToInline = sourceInstructionType == InstructionTypes2.GET_CURRENT_ENTITY || !input.anyChildrenIn(forEachInstruction, it -> it.type() == InstructionTypes.CALL_FUNCTIONS || it.type() == InstructionTypes2.KILL_ENTITY);
                            if (isSafeToInline) {
                                var remapData = new HashMap<Integer, Integer>();
                                remapData.put(forEachInstruction.instruction().<LocationData<?>>get("element").local(), addInstruction.instruction().<LocationData<?>>get("element").local());
                                ArrayList<InstructionModification> modifications = new ArrayList<>();
                                modifications.add(new InstructionModification.RemoveInstruction(addInstruction.path(), 2));
                                modifications.add(new InstructionModification.MoveChildrenFromToNested(forEachInstruction.path(), addInstruction.path().subList(0, addInstruction.path().size() - 1), remapData, false, 1));
                                if (!forEachDirectChild.path().equals(forEachInstruction.path())) {
                                    modifications.add(new InstructionModification.MoveChildrenFromToNested(forEachDirectChild.path(), List.of(), new HashMap<>(), true, 0));
                                }
                                return new OptimizationResult(null, modifications, OptimizationResult.MODIFY_CHILDREN | OptimizationResult.REMOVE_SELF_AND_INLINE_CHILDREN);
                            }
                        }
                    }
                }
            }else if(addInstructions.isEmpty()) {
                return OptimizationResultTypes.REMOVE_SELF;
            }
        }
        return OptimizationResultTypes.KEEP;
    }
}
