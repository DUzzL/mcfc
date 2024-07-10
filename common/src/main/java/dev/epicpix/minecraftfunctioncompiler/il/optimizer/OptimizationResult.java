package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.List;

public record OptimizationResult(List<Instruction> instructions, int flags) {

    public static final int REPLACE_CHILDREN = 0x1;
    public static final int REMOVE_SELF_AND_INLINE_CHILDREN = 0x2;

    public boolean isFlagSet(int flag) {
        return (flags & flag) == flag;
    }

}
