package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData;
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization;

import java.util.ArrayList;
import java.util.List;

public final class DeadCodeEliminationOptimization implements Optimization {

    private record DeadCodeEliminationData(List<Integer> knownNonNulls, List<Integer> unusedScopes) {}
    private record ScopeUsageInfo(boolean usedBreak, boolean usedIndirect, boolean last) {}

    public List<Instruction> optimize(List<Instruction> instructions) {
        return runOptimizations(instructions, new DeadCodeEliminationData(new ArrayList<>(), new ArrayList<>()));
    }

    private List<Instruction> runOptimizations(List<Instruction> instructions, DeadCodeEliminationData dce) {
        var results = new ArrayList<Instruction>();
        for(var it : instructions) {
            results.addAll(runOptimizations(it, dce));
            if(it.type() == InstructionTypes2.EXIT_SCOPE) break;
        }
        return results;
    }

    private List<Instruction> runOptimizations(Instruction instruction, DeadCodeEliminationData dce) {
        var newDce = dce;
        if (instruction.type() == InstructionTypes2.IF_NOT_NULL) {
            var local = instruction.<LocationData<?>>get("local");
            if (local.local() != null) {
                if (newDce.knownNonNulls.contains(local.local())) {
                    return runOptimizations(instruction.children(), newDce);
                } else {
                    ArrayList<Integer> newKnownNonNulls = new ArrayList<>(newDce.knownNonNulls);
                    newKnownNonNulls.add(local.local());
                    newDce = new DeadCodeEliminationData(newKnownNonNulls, dce.unusedScopes);
                }
            } else {
                return runOptimizations(instruction.children(), newDce);
            }
        } else if (instruction.type() == InstructionTypes2.SCOPE) {
            var scope = instruction.<ScopeData>get("scope").id();
            var usage = getScopeUsed(instruction, scope, true, false);
            if (!usage.usedBreak() && usage.last()) {
                ArrayList<Integer> newUnusedScopes = new ArrayList<>(newDce.unusedScopes);
                newUnusedScopes.add(scope);
                newDce = new DeadCodeEliminationData(dce.knownNonNulls, newUnusedScopes);
                if (!usage.usedIndirect) {
                    return runOptimizations(instruction.children(), newDce);
                }
            }
        } else if (instruction.type() == InstructionTypes2.EXIT_SCOPE) {
            var scope = instruction.<ScopeData>get("scope").id();
            if (newDce.unusedScopes.contains(scope)) {
                return List.of();
            }
        }
        return List.of(new Instruction(instruction.type(), instruction.data(), runOptimizations(instruction.children(), newDce)));
    }

    private ScopeUsageInfo getScopeUsed(Instruction instruction, int inScopeId, boolean lastElement, boolean isInLoop) {
        var usedBreak = false;
        var usedIndirect = false;
        var wLoop = isInLoop;
        if (instruction.type() == InstructionTypes2.LIMITER || instruction.type() == InstructionTypes2.FOR_EACH_ENTITY || instruction.type() == InstructionTypes2.FOR_EACH_PLAYER || instruction.type() == InstructionTypes2.FOR_MULTIPLE_SCORE_HOLDERS) {
            wLoop = true;
        } else if (instruction.type() == InstructionTypes2.CHECK_LIMIT) {
            var scope = instruction.<ScopeData>get("scope").id();
            if (scope == inScopeId) {
                return new ScopeUsageInfo(true, true, lastElement);
            }
        } else if (instruction.type() == InstructionTypes2.RUN_SCORE_OPERATION_DIVIDE || instruction.type().equals(InstructionTypes2.RUN_SCORE_OPERATION_MODULO)) {
            var exceptionScope = instruction.<ScopeData>get("exceptionScope").id();
            if (exceptionScope == inScopeId) {
                return new ScopeUsageInfo(true, true, lastElement);
            }
        } else if (instruction.type() == InstructionTypes2.EXIT_SCOPE) {
            var scope = instruction.<ScopeData>get("scope").id();
            if (scope == inScopeId && (!lastElement || wLoop)) {
                return new ScopeUsageInfo(true, false, lastElement);
            }
        }
        for(int i = 0; i<instruction.children().size(); i++) {
            var info = getScopeUsed(instruction.children().get(i), inScopeId, lastElement && i == instruction.children().size() - 1, wLoop);
            usedBreak = usedBreak || info.usedBreak;
            usedIndirect = usedIndirect || info.usedIndirect;
        }
        return new ScopeUsageInfo(usedBreak, usedIndirect, lastElement);
    }
}
