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
    Instruction makeCommandSourceStack(LocationData<CommandSourceStack> result, LocationData<CommandSourceStack> from, @Nullable @InstructionSource LocationData<? extends Entity> entity, @Nullable @InstructionSource LocationData<World> world, @Nullable @InstructionSource LocationData<Vec3> position, @Nullable @InstructionSource LocationData<Vec2> rotation, @Nullable @InstructionSource LocationAnchor anchor);
    Instruction loadStringMacro(@InstructionDestination LocationData<String> result, MacroTemplate<String> macroTemplate);
    Instruction loadIntegerMacro(@InstructionDestination LocationData<Integer> result, MacroTemplate<Integer> macroTemplate);
    @InstructionConditionalJumpFlag
    Instruction checkEntityTypes(@InstructionSource LocationData<? extends Entity> entity, List<DataLocation> types, boolean negated, LabelData failLabel);
}
