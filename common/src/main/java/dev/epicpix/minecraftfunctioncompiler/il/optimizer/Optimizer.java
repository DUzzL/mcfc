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
