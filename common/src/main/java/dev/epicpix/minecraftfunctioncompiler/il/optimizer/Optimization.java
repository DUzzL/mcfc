package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

@FunctionalInterface
public interface Optimization {
    void optimize(OptimizationPass pass);
}
