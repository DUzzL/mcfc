package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;

import java.util.ArrayList;
import java.util.List;

public class InstructionWriter {

    public static final LabelData NULL_LABEL = LabelData.ofId(-1);

    private final ArrayList<Instruction> instructions = new ArrayList<>();
    private final LabelData endLabel;

    private int highestLabelId = 1;

    public InstructionWriter() {
        endLabel = LabelData.ofId(highestLabelId++);
        instructions.add(Instructions.label(endLabel));
    }

    private int indexOfLabel(LabelData label) {
        for(int i = 0; i < instructions.size(); i++) {
            var instr = instructions.get(i);
            if(instr.type() == InstructionTypes.LABEL) {
                if(instr.get(0).equals(label)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public LabelData getEndLabel() {
        return endLabel;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public void addInstructionBefore(Instruction instruction, LabelData before) {
        if(before == NULL_LABEL) return;
        int labelIndex = indexOfLabel(before);
        instructions.add(labelIndex, instruction);
    }

    public void addInstructionAfter(Instruction instruction, LabelData after) {
        if(after == NULL_LABEL) return;
        int labelIndex = indexOfLabel(after);
        instructions.add(labelIndex + 1, instruction);
    }

    public LabelData createLabelBefore(LabelData before) {
        if(before == NULL_LABEL) return NULL_LABEL;
        LabelData label = createDeferredLabel();
        addInstructionBefore(Instructions.label(label), before);
        return label;
    }

    public LabelData createLabelAfter(LabelData after) {
        if(after == NULL_LABEL) return NULL_LABEL;
        LabelData label = createDeferredLabel();
        addInstructionAfter(Instructions.label(label), after);
        return label;
    }

    public LabelData createDeferredLabel() {
        return LabelData.ofId(highestLabelId++);
    }

}
