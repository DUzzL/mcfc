package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCommandSourceStack;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TServerLevel;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec2;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec3;
import org.jspecify.annotations.Nullable;

public record CompilationContextData(
        LocationData<TCommandSourceStack> commandSourceStack,
        @Nullable LocationData<? extends TEntity> currentEntity,
        @Nullable LocationData<TServerLevel> currentWorld,
        @Nullable LocationData<TVec3> currentPosition,
        @Nullable LocationData<TVec2> currentRotation,
        @Nullable LocationAnchor currentAnchor,
        LabelData contextEndLabel
) {

    public static CompilationContextData create(LocationData<TCommandSourceStack> commandSourceStack, LabelData endLabel) {
        return new CompilationContextData(commandSourceStack, null, null, null, null, null, endLabel);
    }

    public CompilationContextData withCurrentEntity(LocationData<? extends TEntity> currentEntity) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentWorld(LocationData<TServerLevel> currentWorld) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentPosition(LocationData<TVec3> currentPosition) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentRotation(LocationData<TVec2> currentRotation) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentAnchor(LocationAnchor currentAnchor) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withContextLabel(LabelData codeLocation) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, codeLocation);
    }

    public void writeInstruction(InstructionWriter instructionWriter, Instruction instruction) {
        instructionWriter.addInstructionBefore(instruction, contextEndLabel);
    }

    public LabelData writeLabel(InstructionWriter instructionWriter) {
        return instructionWriter.createLabelBefore(contextEndLabel);
    }
}
