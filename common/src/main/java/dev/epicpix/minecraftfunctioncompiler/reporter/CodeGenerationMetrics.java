package dev.epicpix.minecraftfunctioncompiler.reporter;

public record CodeGenerationMetrics(
    int totalCommandCount,
    int totalUnoptimizedHighLevelInstructionCount,
    int totalHighLevelInstructionCount,
    int totalLowLevelInstructionCount,
    int totalFunctionClassFileSize,
    int totalDataClassFileSize
) {}
