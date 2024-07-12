package dev.epicpix.minecraftfunctioncompiler.reporter;

public record TimingData(
        double parse,
        double compile,
        double classLoad,
        double handleLoad
) {}
