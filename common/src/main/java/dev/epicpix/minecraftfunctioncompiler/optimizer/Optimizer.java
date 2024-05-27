package dev.epicpix.minecraftfunctioncompiler.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.CommonSubexpressionEliminationOptimization;
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.DeadCodeEliminationOptimization;
import dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations.ScopeInstructionMergeOptimization;

import java.util.ArrayList;
import java.util.List;

public final class Optimizer {
    private Optimizer() {}

    private static final Optimization[] OPTIMIZATIONS = new Optimization[]{
            DeadCodeEliminationOptimization.INSTANCE,
            new CommonSubexpressionEliminationOptimization(),
            ScopeInstructionMergeOptimization.INSTANCE,
    };

    public static List<Instruction> optimize(List<Instruction> instructions, ArrayList<OptimizationStatistics> statistics) {
        statistics.add(new OptimizationStatistics("start", Instruction.countInstructions(instructions), 0.0));
        var start = System.nanoTime();
        var previousInstructions = instructions;
        do {
            previousInstructions = instructions;
            var startOptim = System.nanoTime();
            for(var optimization : OPTIMIZATIONS) {
                instructions = optimization.optimize(instructions);
            }
            var endOptim = System.nanoTime();
            statistics.add(new OptimizationStatistics("optimization_pass", Instruction.countInstructions(instructions), (endOptim - startOptim)/1000000000.0));
        } while(instructions != previousInstructions);
        var end = System.nanoTime();
        statistics.add(new OptimizationStatistics("end", Instruction.countInstructions(instructions), (end - start)/1000000000.0));
        return instructions;
    }

}
