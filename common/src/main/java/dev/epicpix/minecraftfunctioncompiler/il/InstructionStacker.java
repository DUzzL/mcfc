package dev.epicpix.minecraftfunctioncompiler.il;

public class InstructionStacker {
    public final Instruction base;
    public Instruction current;

    public InstructionStacker(Instruction base) {
        this.base = base;
        current = base;
    }

    public void stackInstruction(Instruction instruction) {
        current.with(instruction);
        current = instruction;
    }
}
