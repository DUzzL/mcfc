package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

public record OptimizationStatistics(
        String type,
        int instructionCount,
        double timeTaken
) {}
