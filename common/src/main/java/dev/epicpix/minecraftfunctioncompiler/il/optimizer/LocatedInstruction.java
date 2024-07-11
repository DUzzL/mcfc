package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.List;

public record LocatedInstruction(InstructionPath path, Instruction instruction) {
    public LocatedInstruction getIndexed(int index) {
        return new LocatedInstruction(path.addPathNode(index), instruction.children().get(index));
    }
}
