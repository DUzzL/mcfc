package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

public record CompilationContextData(
        LocationData<CommandSourceStack> commandSourceStack,
        LocationData<Entity> currentEntity,
        LocationData<World> currentWorld,
        LocationData<Vec3> currentPosition,
        LocationData<Vec2> currentRotation,
        LocationAnchor currentAnchor,
        LabelData contextEndLabel
) {

    public static CompilationContextData create(LocationData<CommandSourceStack> commandSourceStack, LabelData endLabel) {
        return new CompilationContextData(commandSourceStack, null, null, null, null, null, endLabel);
    }

    public CompilationContextData withCurrentEntity(LocationData<Entity> currentEntity) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentWorld(LocationData<World> currentWorld) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentPosition(LocationData<Vec3> currentPosition) {
        return new CompilationContextData(commandSourceStack, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, contextEndLabel);
    }

    public CompilationContextData withCurrentRotation(LocationData<Vec2> currentRotation) {
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
