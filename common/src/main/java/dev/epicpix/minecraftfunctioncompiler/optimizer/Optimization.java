package dev.epicpix.minecraftfunctioncompiler.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.List;

public interface Optimization {
    List<Instruction> optimize(List<Instruction> instructions);
}
