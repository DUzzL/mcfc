package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import java.util.List;

public record InstructionModification(List<Integer> path, InstructionModificationType type) {}
