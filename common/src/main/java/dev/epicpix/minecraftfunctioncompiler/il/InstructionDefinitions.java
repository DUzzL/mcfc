package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionConditionalJumpFlag;
import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes3", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions3")
interface InstructionDefinitions {
    // Minecraft specific instructions that have not been converted into data.instructions yet
    Instruction callFunctions(@Nullable LocationData<Long> result, List<DataLocation> functions, LocationData<CommandSourceStack> stack, @Nullable LocationData<NbtCompound> arguments);
    Instruction makeCommandSourceStack(LocationData<CommandSourceStack> result, LocationData<CommandSourceStack> from, @Nullable LocationData<? extends Entity> entity, @Nullable LocationData<World> world, @Nullable LocationData<Vec3> position, @Nullable LocationData<Vec2> rotation, @Nullable LocationAnchor anchor);
    Instruction loadStringMacro(LocationData<String> result, MacroTemplate<String> macroTemplate);
    Instruction loadIntegerMacro(LocationData<Integer> result, MacroTemplate<Integer> macroTemplate);
    @InstructionConditionalJumpFlag
    Instruction checkEntityTypes(LocationData<? extends Entity> entity, List<DataLocation> types, boolean negated, LabelData failLabel);
}
