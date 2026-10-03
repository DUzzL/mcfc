package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.ArrayList;
import java.util.List;

public class Optimizer {

    // Safety cap on fixpoint rounds for very large functions; each round is near-linear.
    private static final int LARGE_FUNCTION_THRESHOLD = 3_000;
    private static final int MAX_ROUNDS_LARGE = 5;

    public static List<Instruction> optimize(List<Instruction> instructions, LengauerTarjan tarjan) {
        int highestLocal = -1;
        for(var i : instructions) {
            for(var data : i.data()) {
                if(data instanceof LocationData<?> loc && loc.local() != null) {
                    highestLocal = Math.max(highestLocal, loc.local());
                }
            }
        }
        OptimizationContext context = new OptimizationContext(new ArrayList<>(instructions), highestLocal + 1, tarjan);
        doOptimizations(context, context.getInstructionCount() > LARGE_FUNCTION_THRESHOLD);
        return List.copyOf(context.getInstructions());
    }

    private static void doOptimizations(OptimizationContext context, boolean isLargeFunction) {
        var pass = new OptimizationPass(-1, context);
        int maxRounds = isLargeFunction ? MAX_ROUNDS_LARGE : Integer.MAX_VALUE;
        int rounds = 0;
        boolean queueNext = true;
        boolean recheckDeadCode = true;
        while(queueNext && rounds++ < maxRounds) {
            queueNext = false;
            for(var opt : Optimizations.OPTIMIZATIONS) {
                if(recheckDeadCode) {
                    context.removeUnreachable();
                    context.compact();
                    recheckDeadCode = false;
                }
                if(context.getInstructionCount() == 0) return;
                for(int i = 0; i < context.getInstructionCount(); i++) {
                    if(OptimizationContext.isRemoved(context.getInstruction(i))) continue;
                    pass.setThisInstruction(i);
                    opt.optimize(pass);
                    int resetIndex = pass.takeResetIndex();
                    if(resetIndex != Integer.MAX_VALUE) {
                        i = Math.min(i, resetIndex) - 1;
                        queueNext = true;
                        recheckDeadCode = true;
                    }
                }
                context.compact();
            }
        }
        // Collapse adjacent labels created by the last round.
        pass.setThisInstruction(0);
        if(context.getInstructionCount() > 0) Optimizations.bulkMergeAdjacentLabels(pass);
        context.compact();
    }

}
