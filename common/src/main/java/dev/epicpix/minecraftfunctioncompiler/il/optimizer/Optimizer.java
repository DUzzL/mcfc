package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Optimizer {
    private Optimizer() {}

    public static List<Instruction> optimize(List<Instruction> instructions, ArrayList<OptimizationStatistics> statistics) {
        statistics.add(new OptimizationStatistics("start", Instruction.countInstructions(instructions), 0.0));
        var start = System.nanoTime();

        List<Instruction> prev;
        do {
            prev = instructions;
            var startOptimization = System.nanoTime();
            instructions = runOptimizations(instructions);
            var endOptimization = System.nanoTime();
            statistics.add(new OptimizationStatistics("optimization_pass", Instruction.countInstructions(instructions), (endOptimization - startOptimization)/1000000000.0));
        } while(!prev.equals(instructions));

        var end = System.nanoTime();
        statistics.add(new OptimizationStatistics("end", Instruction.countInstructions(instructions), (end - start)/1000000000.0));
        return instructions;
    }

    private static List<Instruction> runOptimizations(List<Instruction> instructions) {
        for(var optimization : Optimizations.OPTIMIZATIONS) {
            instructions = applyOptimization(instructions, optimization);
        }
        return instructions;
    }

    private static List<Instruction> applyOptimization(List<Instruction> instructions, Optimization optimization) {
        ArrayList<Instruction> result = new ArrayList<>();
        for(var instr : instructions) {
            result.addAll(applyOptimizationInner(instr, optimization));
        }
        return result;
    }

    private static List<Instruction> applyOptimizationInner(Instruction input, Optimization optimization) {
        ArrayList<Instruction> newChildren = new ArrayList<>();
        for(var child : input.children()) {
            newChildren.addAll(applyOptimizationInner(child, optimization));
        }
        var preNew = new Instruction(input.type(), input.data(), newChildren);
        var result = optimization.runOptimization(new OptimizationInput(preNew));
        if(result.isFlagSet(OptimizationResult.MODIFY_CHILDREN)) {
            var modifications = new ArrayList<>(result.modifications());
            modifications.sort((a, b) -> {
                int length = Math.min(a.path().size(), b.path().size());
                for(int i = 0; i<length; i++) {
                    int va = a.path().get(i);
                    int vb = b.path().get(i);
                    int c = Integer.compare(va, vb);
                    if(c == 0) continue;
                    return -c;
                }
                return -Integer.compare(a.path().size(), b.path().size());
            });
            for(var modification : modifications) {
                var beforePath = modification.path().subList(0, modification.path().size() - 1);
                var instr = preNew;
                for(var loc : beforePath) {
                    instr = instr.children().get(loc);
                }
                if(modification.type() == InstructionModificationType.REMOVE) {
                    instr.children().remove((int) modification.path().get(modification.path().size() - 1));
                }
            }
        }
        if(result.isFlagSet(OptimizationResult.REMOVE_SELF_AND_INLINE_CHILDREN)) {
            if(result.isFlagSet(OptimizationResult.REPLACE_CHILDREN)) {
                return result.instructions();
            }else {
                return newChildren;
            }
        }else if(result.isFlagSet(OptimizationResult.REPLACE_CHILDREN)) {
            return List.of(new Instruction(input.type(), input.data(), result.instructions()));
        }
        return List.of(preNew);
    }

}
