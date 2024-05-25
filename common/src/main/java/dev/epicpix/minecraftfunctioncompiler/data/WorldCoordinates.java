package dev.epicpix.minecraftfunctioncompiler.data;

public record WorldCoordinates(
        double x,
        boolean isXRelative,
        double y,
        boolean isYRelative,
        double z,
        boolean isZRelative
) {}
