package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionDestination;
import dev.epicpix.annotations.InstructionSource;
import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions")
interface InstructionDefinitions {
    // Minecraft specific instructions
    Instruction callFunctions(List<DataLocation> functions, @Nullable @InstructionSource LocationData<Entity> entity, @Nullable @InstructionSource LocationData<World> world, @Nullable @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Vec2> rotation, @Nullable @InstructionSource LocationAnchor anchor, @Nullable @InstructionSource LocationData<NbtCompound> arguments);
    Instruction loadMacro(MacroTemplate<String> macroTemplate, @InstructionDestination LocationData<String> string);
    Instruction checkScoreCompare(@InstructionSource LocationData<Score> target, @InstructionSource LocationData<Score> source, ScoreboardCompareOperation compareOperation);
    Instruction applyLocalPositionWorld(@InstructionSource LocationData<Vec3> fromPosition, @InstructionSource LocationData<Vec2> fromRotation, @InstructionSource LocationData<Double> yOffset, @InstructionDestination LocationData<Vec3> to, WorldCoordinates position);
    Instruction applyPositionWorld(@InstructionSource LocationData<Vec3> from, @InstructionDestination LocationData<Vec3> to, WorldCoordinates position);
    Instruction applyRotationWorld(@InstructionSource LocationData<Vec2> from, @InstructionDestination LocationData<Vec2> to, RotationCoordinates rotation);
    Instruction checkEntityTypes(@InstructionSource LocationData<Entity> entity, List<DataLocation> types, boolean negated);
    Instruction alignPosition(@InstructionSource LocationData<Vec3> position, @InstructionDestination LocationData<Vec3> result, SwizzleArgument swizzle);
    Instruction checkIntRange(@InstructionSource LocationData<Integer> value, @Nullable IntRangeArgument range);
    Instruction checkNotIntRange(@InstructionSource LocationData<Integer> value, @Nullable IntRangeArgument range);
    Instruction checkFloatRange(@InstructionSource LocationData<Float> value, @Nullable FloatRangeArgument range);
    Instruction checkDoubleRange(@InstructionSource LocationData<Double> value, @Nullable DoubleRangeArgument range);
    Instruction checkRotationYaw(@InstructionSource LocationData<Vec2> rotation, @Nullable FloatRangeArgument yaw);
    Instruction checkRotationPitch(@InstructionSource LocationData<Vec2> rotation, @Nullable FloatRangeArgument pitch);
}
