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
    Instruction callFunctions(@Nullable @InstructionDestination LocationData<Long> result, List<DataLocation> functions, LocationData<CommandSourceStack> stack, @Nullable @InstructionSource LocationData<NbtCompound> arguments);
    Instruction makeCommandSourceStack(LocationData<CommandSourceStack> result, LocationData<CommandSourceStack> from, @Nullable @InstructionSource LocationData<Entity> entity, @Nullable @InstructionSource LocationData<World> world, @Nullable @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Vec2> rotation, @Nullable @InstructionSource LocationAnchor anchor);
    Instruction loadMacro(@InstructionDestination LocationData<String> result, MacroTemplate<String> macroTemplate);
    @InstructionConditionalJumpFlag
    Instruction checkScoreCompare(@InstructionSource LocationData<Score> target, @InstructionSource LocationData<Score> source, ScoreboardCompareOperation compareOperation, LabelData failLabel);
    Instruction applyLocalPositionWorld(@InstructionDestination LocationData<Vec3> result, @InstructionSource LocationData<Vec3> fromPosition, @InstructionSource LocationData<Vec2> fromRotation, @InstructionSource LocationData<Double> yOffset, WorldCoordinates position);
    Instruction applyPositionWorld(@InstructionDestination LocationData<Vec3> result, @InstructionSource LocationData<Vec3> from, WorldCoordinates position);
    Instruction applyRotationWorld(@InstructionDestination LocationData<Vec2> result, @InstructionSource LocationData<Vec2> from, RotationCoordinates rotation);
    @InstructionConditionalJumpFlag
    Instruction checkEntityTypes(@InstructionSource LocationData<Entity> entity, List<DataLocation> types, boolean negated, LabelData failLabel);
    Instruction alignPosition(@InstructionDestination LocationData<Vec3> result, @InstructionSource LocationData<Vec3> position, SwizzleArgument swizzle);
    Instruction mergePositions(@InstructionDestination LocationData<Vec3> result, @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Double> x, @Nullable @InstructionSource LocationData<Double> y, @Nullable @InstructionSource LocationData<Double> z);
}
