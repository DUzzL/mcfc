package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;

public final class Optimizations {
    private Optimizations() {}

    static final Optimization[] OPTIMIZATIONS = {
            Optimizations::removeUnnecessaryInstructions,
    };

    private static OptimizationResult removeUnnecessaryInstructions(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.ALWAYS) return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
        if(instr.type() == InstructionTypes2.NEVER) return OptimizationResultTypes.REMOVE_SELF;
        return OptimizationResultTypes.KEEP;
    }
}
