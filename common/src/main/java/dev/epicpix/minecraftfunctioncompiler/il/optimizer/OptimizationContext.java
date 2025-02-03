package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptimizationContext {

    final LengauerTarjan tarjan;
    final ArrayList<Instruction> instructions;
    private int nextLocalId;

    OptimizationContext(ArrayList<Instruction> instructions, int nextLocalId, LengauerTarjan tarjan) {
        this.instructions = instructions;
        this.nextLocalId = nextLocalId;
        this.tarjan = tarjan;
    }

    public Instruction getInstruction(int index) {
        if(index < 0 || index >= instructions.size()) return null;
        return instructions.get(index);
    }

    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    public int nextLocalId() {
        return nextLocalId++;
    }
}
