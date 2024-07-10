package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

@FunctionalInterface
public interface Optimization {
    OptimizationResult runOptimization(OptimizationInput input);
}
