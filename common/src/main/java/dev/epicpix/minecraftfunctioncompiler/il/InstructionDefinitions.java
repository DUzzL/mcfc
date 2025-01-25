package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionConditionalJumpFlag;
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
    // Minecraft specific instructions that have not been converted into data.instructions yet
    Instruction callFunctions(List<DataLocation> functions, LocationData<CommandSourceStack> stack, @Nullable @InstructionSource LocationData<NbtCompound> arguments, @Nullable @InstructionDestination LocationData<Long> returnValue);
    Instruction makeCommandSourceStack(LocationData<CommandSourceStack> from, LocationData<CommandSourceStack> to, @Nullable @InstructionSource LocationData<Entity> entity, @Nullable @InstructionSource LocationData<World> world, @Nullable @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Vec2> rotation, @Nullable @InstructionSource LocationAnchor anchor);
    Instruction loadMacro(MacroTemplate<String> macroTemplate, @InstructionDestination LocationData<String> string);
    @InstructionConditionalJumpFlag
    Instruction checkScoreCompare(@InstructionSource LocationData<Score> target, @InstructionSource LocationData<Score> source, ScoreboardCompareOperation compareOperation, LabelData failLabel);
    Instruction applyLocalPositionWorld(@InstructionSource LocationData<Vec3> fromPosition, @InstructionSource LocationData<Vec2> fromRotation, @InstructionSource LocationData<Double> yOffset, @InstructionDestination LocationData<Vec3> to, WorldCoordinates position);
    Instruction applyPositionWorld(@InstructionSource LocationData<Vec3> from, @InstructionDestination LocationData<Vec3> to, WorldCoordinates position);
    Instruction applyRotationWorld(@InstructionSource LocationData<Vec2> from, @InstructionDestination LocationData<Vec2> to, RotationCoordinates rotation);
    @InstructionConditionalJumpFlag
    Instruction checkEntityTypes(@InstructionSource LocationData<Entity> entity, List<DataLocation> types, boolean negated, LabelData failLabel);
    Instruction alignPosition(@InstructionSource LocationData<Vec3> position, @InstructionDestination LocationData<Vec3> result, SwizzleArgument swizzle);
    Instruction mergePositions(@InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Double> x, @Nullable @InstructionSource LocationData<Double> y, @Nullable @InstructionSource LocationData<Double> z, @InstructionDestination LocationData<Vec3> result);
}
