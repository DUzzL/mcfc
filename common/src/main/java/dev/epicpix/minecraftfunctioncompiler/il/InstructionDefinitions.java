package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionDestination;
import dev.epicpix.annotations.InstructionSource;
import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions")
interface InstructionDefinitions {
    // Minecraft specific instructions
    Instruction callFunctions(List<DataLocation> functions, @Nullable @InstructionSource LocationData entity, @Nullable @InstructionSource LocationData world, @Nullable @InstructionSource LocationData position, @Nullable @InstructionSource LocationData rotation);
    Instruction checkInScoreRange(@InstructionSource LocationData score, IntRangeArgument range);
    Instruction checkNotInScoreRange(@InstructionSource LocationData score, IntRangeArgument range);
    Instruction checkScoreCompare(@InstructionSource LocationData target, @InstructionSource LocationData source, ScoreboardCompareOperation compareOperation);
    Instruction applyPositionWorld(@InstructionSource LocationData from, @InstructionDestination LocationData to, WorldCoordinates position);
    Instruction checkEntityTypes(@InstructionSource LocationData entity, List<DataLocation> types, boolean negated);
    Instruction alignPosition(@InstructionSource LocationData position, @InstructionDestination LocationData result, SwizzleArgument swizzle);
    Instruction checkGamemode(@InstructionSource LocationData entity, Gamemode gamemode, boolean negated);
    Instruction setGamemode(@InstructionSource LocationData entity, Gamemode gamemode);
    Instruction checkRotationYaw(@InstructionSource LocationData rotation, @Nullable FloatRangeArgument yaw);
    Instruction checkRotationPitch(@InstructionSource LocationData rotation, @Nullable FloatRangeArgument pitch);
}
