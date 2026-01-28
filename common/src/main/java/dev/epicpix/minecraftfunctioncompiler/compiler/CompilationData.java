package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public record CompilationData<T>(@NonNull CompilationContext context, @NonNull CompilationContextData data, T content, int stackCount) implements AutoCloseable {
    public void writeInstruction(@NonNull Instruction instr) {
        data.writeInstruction(context.instructionWriter, instr);
    }
    public @NonNull LabelData endLabel() {
        return data.contextEndLabel();
    }
    @SuppressWarnings("unchecked")
    public <R> @NonNull CompilationData<R> changeType() {
        return new CompilationData<>(context, data, (R) content, stackCount);
    }
    public void close() {
        for(int i = 0; i<stackCount; i++) {
            context.exit();
        }
    }
}
