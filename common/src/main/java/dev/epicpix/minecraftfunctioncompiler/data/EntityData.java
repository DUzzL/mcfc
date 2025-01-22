package dev.epicpix.minecraftfunctioncompiler.data;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;

public record EntityData(InstructionTarget.FieldTarget field, boolean summonable) {}
