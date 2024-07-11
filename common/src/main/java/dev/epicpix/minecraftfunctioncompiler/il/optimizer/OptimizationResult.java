package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;

import java.util.List;
import java.util.Map;

public record OptimizationResult(List<Instruction> instructions, List<InstructionModification> modifications, Map<Integer, Integer> remap, InstructionType newType, Map<String, Object> newData, int flags) {

    public static final int REPLACE_CHILDREN = 0x1;
    public static final int REMOVE_SELF_AND_INLINE_CHILDREN = 0x2;
    public static final int MODIFY_CHILDREN = 0x4;
    public static final int REMAP_VALUES = 0x8;
    public static final int REPLACE_SELF_TYPE = 0x10;
    public static final int REPLACE_SELF_DATA = 0x20;

    public boolean isFlagSet(int flag) {
        return (flags & flag) == flag;
    }

    public static OptimizationResult remapWithFlags(Map<Integer, Integer> remap, int flags) {
        return new OptimizationResult(null, null, remap, null, null, flags | REMAP_VALUES);
    }

    public static OptimizationResult modificationsWithFlags(List<InstructionModification> modifications, int flags) {
        return new OptimizationResult(null, modifications, null, null, null, flags | MODIFY_CHILDREN);
    }

}
