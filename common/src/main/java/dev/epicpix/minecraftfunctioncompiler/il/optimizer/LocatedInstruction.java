package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.List;

public record LocatedInstruction(List<Integer> path, Instruction instruction) {}
