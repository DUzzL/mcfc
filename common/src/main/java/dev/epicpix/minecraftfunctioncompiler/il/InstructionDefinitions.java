package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionDestination;
import dev.epicpix.annotations.InstructionSource;
import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions")
interface InstructionDefinitions {
    // Minecraft specific instructions
    Instruction callFunctions(List<DataLocation> functions, @Nullable @InstructionSource LocationData<Entity> entity, @Nullable @InstructionSource LocationData<World> world, @Nullable @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Vec2> rotation);
    Instruction checkInScoreRange(@InstructionSource LocationData<Score> score, IntRangeArgument range);
    Instruction checkNotInScoreRange(@InstructionSource LocationData<Score> score, IntRangeArgument range);
    Instruction checkScoreCompare(@InstructionSource LocationData<Score> target, @InstructionSource LocationData<Score> source, ScoreboardCompareOperation compareOperation);
    Instruction applyPositionWorld(@InstructionSource LocationData<Vec3> from, @InstructionDestination LocationData<Vec3> to, WorldCoordinates position);
    Instruction checkEntityTypes(@InstructionSource LocationData<Entity> entity, List<DataLocation> types, boolean negated);
    Instruction alignPosition(@InstructionSource LocationData<Vec3> position, @InstructionDestination LocationData<Vec3> result, SwizzleArgument swizzle);
    Instruction checkRotationYaw(@InstructionSource LocationData<Vec2> rotation, @Nullable FloatRangeArgument yaw);
    Instruction checkRotationPitch(@InstructionSource LocationData<Vec2> rotation, @Nullable FloatRangeArgument pitch);
}
