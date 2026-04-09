package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;

public class Optimizer {

    // Functions with more IL instructions than this threshold get a reduced
    // optimization pass: inlineLists is skipped (its bulk ArrayList.remove/add
    // is O(instructions * moves) and dominates compile time for huge config
    // functions), and the outer loop is capped at MAX_ROUNDS_LARGE to prevent
    // quadratic blowup from repeated full-function sweeps.
    private static final int LARGE_FUNCTION_THRESHOLD = 3_000;
    private static final int MAX_ROUNDS_LARGE          = 5;

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
        return context.getInstructions();
    }

    private static void removeDeadCodeQuick(OptimizationPass pass) {
        var instrCount = pass.context().getInstructionCount();
        pass.context().ensureCachedMapCapacity();
        pass.generateInstructionEdges(pass.context().deadCodeForward, null);
        var stack = new IntArrayList(instrCount);
        var traversed = new boolean[instrCount];
        stack.push(0);
        while(!stack.isEmpty()) {
            var index = stack.popInt();
            traversed[index] = true;
            for(var it = pass.context().deadCodeForward.get(index).intIterator(); it.hasNext(); ) {
                var next = it.nextInt();
                if(traversed[next]) continue;
                stack.push(next);
            }
        }
        for(int i = instrCount - 1; i >= 0; i--) {
            if(!traversed[i]) pass.removeInstruction(i);
        }
        pass.takeResetIndex();
    }

    private static void doOptimizations(OptimizationContext context, boolean isLargeFunction) {
        boolean queueNext = true;
        var pass = new OptimizationPass(-1, context);
        // Large functions use a reduced optimisation set (no inlineLists) and a
        // round cap so that config-style 50k-instruction functions don't spend
        // minutes in quadratic ArrayList.remove / cache-rebuild loops.
        var optimizations = isLargeFunction ? Optimizations.LARGE_FUNCTION_OPTIMIZATIONS : Optimizations.OPTIMIZATIONS;
        int maxRounds = isLargeFunction ? MAX_ROUNDS_LARGE : Integer.MAX_VALUE;
        int rounds = 0;

        boolean recheckDeadCode = true;
        while(queueNext && rounds++ < maxRounds) {
            queueNext = false;

            for(var opt : optimizations) {
                if(context.getInstructionCount() == 0) return;
                if(recheckDeadCode) {
                    removeDeadCodeQuick(pass);
                    recheckDeadCode = false;
                }
                for(int i = 0; i<context.getInstructionCount(); i++) {
                    pass.setThisInstruction(i);
                    opt.optimize(pass);
                    int resetIndex = pass.takeResetIndex();
                    if(resetIndex != Integer.MAX_VALUE) {
                        i = resetIndex - 1;
                        queueNext = true;
                        recheckDeadCode = true;
                    }
                }
            }
        }
    }

}
