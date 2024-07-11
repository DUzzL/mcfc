package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationResult.REMOVE_SELF_AND_INLINE_CHILDREN;
import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationResult.REPLACE_CHILDREN;

public final class OptimizationResultTypes {
    private OptimizationResultTypes() {}

    public static final OptimizationResult KEEP = new OptimizationResult(null, null, null, null, null, 0);
    public static final OptimizationResult INLINE_AND_REMOVE_SELF = new OptimizationResult(null, null, null, null, null, REMOVE_SELF_AND_INLINE_CHILDREN);
    public static final OptimizationResult REMOVE_SELF = new OptimizationResult(List.of(), null, null, null, null,  REPLACE_CHILDREN | REMOVE_SELF_AND_INLINE_CHILDREN);

}
