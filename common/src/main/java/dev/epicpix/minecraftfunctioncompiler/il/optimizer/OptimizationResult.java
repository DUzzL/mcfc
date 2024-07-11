package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.HashMap;
import java.util.List;

public record OptimizationResult(List<Instruction> instructions, List<InstructionModification> modifications, HashMap<Integer, Integer> remap, int flags) {

    public static final int REPLACE_CHILDREN = 0x1;
    public static final int REMOVE_SELF_AND_INLINE_CHILDREN = 0x2;
    public static final int MODIFY_CHILDREN = 0x4;
    public static final int REMAP_VALUES = 0x8;

    public boolean isFlagSet(int flag) {
        return (flags & flag) == flag;
    }

}
