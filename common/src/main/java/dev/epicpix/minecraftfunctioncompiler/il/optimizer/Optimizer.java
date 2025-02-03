package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

public class Optimizer {

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
        doOptimizations(context);
        return context.getInstructions();
    }

    private static void removeDeadCodeQuick(OptimizationPass pass) {
        var instrCount = pass.context().getInstructionCount();
        var forward = new Int2ObjectOpenHashMap<IntSet>(instrCount);
        pass.generateInstructionEdges(forward, null);
        var stack = new IntArrayList(instrCount);
        var traversed = new boolean[instrCount];
        stack.push(0);
        while(!stack.isEmpty()) {
            var index = stack.popInt();
            traversed[index] = true;
            for(var it = forward.get(index).intIterator(); it.hasNext(); ) {
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

    private static void doOptimizations(OptimizationContext context) {
        boolean queueNext = true;
        var pass = new OptimizationPass(-1, context);

        boolean recheckDeadCode = true;
        while(queueNext) {
            queueNext = false;

            for(var opt : Optimizations.OPTIMIZATIONS) {
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
