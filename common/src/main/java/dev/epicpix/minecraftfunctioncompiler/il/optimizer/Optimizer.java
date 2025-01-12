package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.ArrayList;
import java.util.List;

public class Optimizer {

    public static List<Instruction> optimize(List<Instruction> instructions) {
        int highestLocal = -1;
        for(var i : instructions) {
            for(var data : i.data()) {
                if(data instanceof LocationData<?> loc && loc.local() != null) {
                    highestLocal = Math.max(highestLocal, loc.local());
                }
            }
        }
        OptimizationContext context = new OptimizationContext(new ArrayList<>(instructions), highestLocal + 1);
        doOptimizations(context);
        return context.getInstructions();
    }

    private static void doOptimizations(OptimizationContext context) {
        boolean queueNext = true;
        var pass = new OptimizationPass(-1, context);
        while(queueNext) {
            queueNext = false;

            for(var opt : Optimizations.OPTIMIZATIONS) {
                for(int i = 0; i<context.getInstructionCount(); i++) {
                    pass.setThisInstruction(i);
                    opt.optimize(pass);
                    int resetIndex = pass.takeResetIndex();
                    if(resetIndex != Integer.MAX_VALUE) {
                        i = resetIndex - 1;
                        queueNext = true;
                    }
                }
            }
        }
    }

}
