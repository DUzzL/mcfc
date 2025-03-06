package dev.epicpix.minecraftfunctioncompiler.reporter;

public record FunctionCountsData(
        int foundFunctionCount,
        int parsedFunctionCount,
        int compiledFunctionCount,
        int loadedFunctionCount
) {}
