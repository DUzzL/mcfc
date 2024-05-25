package dev.epicpix.minecraftfunctioncompiler.optimizer;

public record OptimizationStatistics(
        String type,
        int instructionCount,
        double timeTaken
) {}
