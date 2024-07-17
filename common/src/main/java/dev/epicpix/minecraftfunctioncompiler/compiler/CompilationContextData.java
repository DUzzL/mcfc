package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.data.locations.Entity;
import dev.epicpix.minecraftfunctioncompiler.data.locations.Vec2;
import dev.epicpix.minecraftfunctioncompiler.data.locations.Vec3;
import dev.epicpix.minecraftfunctioncompiler.data.locations.World;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.function.Consumer;

public record CompilationContextData(
        int highestId,
        LocationData<Entity> currentEntity,
        LocationData<World> currentWorld,
        LocationData<Vec3> currentPosition,
        LocationData<Vec2> currentRotation,
        LocationAnchor currentAnchor,
        Consumer<Instruction> addInstructionConsumer
) {

    public void writeInstruction(Instruction instruction) {
        addInstructionConsumer.accept(instruction);
    }

    public static CompilationContextData create(Consumer<Instruction> addInstructionConsumer) {
        return new CompilationContextData(0, null, null, null, null, null, addInstructionConsumer);
    }

    public CompilationContextData incrementedId(int by) {
        return new CompilationContextData(highestId + by, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withCurrentEntity(LocationData<Entity> currentEntity) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withCurrentWorld(LocationData<World> currentWorld) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withCurrentPosition(LocationData<Vec3> currentPosition) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withCurrentRotation(LocationData<Vec2> currentRotation) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withCurrentAnchor(LocationAnchor currentAnchor) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withInstructionAdder(Consumer<Instruction> addInstructionConsumer) {
        return new CompilationContextData(highestId, currentEntity, currentWorld, currentPosition, currentRotation, currentAnchor, addInstructionConsumer);
    }

    public CompilationContextData withBaseInstruction(Instruction instruction) {
        return withInstructionAdder(instruction::with);
    }

}
