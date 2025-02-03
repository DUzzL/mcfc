package dev.epicpix.minecraftfunctioncompiler.reporter;

public record TimingData(
        double parse,
        double compile,
        double optimize,
        double classLoad,
        double handleLoad
) {}
