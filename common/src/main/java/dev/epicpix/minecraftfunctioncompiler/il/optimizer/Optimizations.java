package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData;

import java.util.ArrayList;

import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationInput.addPathIndex;

public final class Optimizations {
    private Optimizations() {}

    static final Optimization[] OPTIMIZATIONS = {
            Optimizations::removeExitScopeDeadCode,
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
                        modifications.add(new InstructionModification(addPathIndex(before.path(), i), InstructionModificationType.REMOVE));
                    }
                }
            }
            if(!modifications.isEmpty()) {
                return new OptimizationResult(null, modifications, OptimizationResult.MODIFY_CHILDREN);
            }
        }
        return OptimizationResultTypes.KEEP;
    }
}
