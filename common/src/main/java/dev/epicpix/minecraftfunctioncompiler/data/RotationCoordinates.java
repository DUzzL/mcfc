package dev.epicpix.minecraftfunctioncompiler.data;

public record RotationCoordinates(
        double yaw,
        boolean isYawRelative,
        double pitch,
        boolean isPitchRelative
) {}
