package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;

public record CompilationData<T>(CompilationContext context, CompilationContextData data, T content, int stackCount) implements AutoCloseable {
    public void writeInstruction(Instruction instr) {
        data.writeInstruction(context.instructionWriter, instr);
    }
    public LabelData endLabel() {
        return data.contextEndLabel();
    }
    @SuppressWarnings("unchecked")
    public <R> CompilationData<R> changeType() {
        return new CompilationData<>(context, data, (R) content, stackCount);
    }
    public void close() {
        for(int i = 0; i<stackCount; i++) {
            context.exit();
        }
    }
}
