package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions2")
interface InstructionDefinitions {
    // Minecraft specific instructions that have not been converted into data.instructions yet
    Instruction callFunctions(@Nullable LocationData<Long> result, List<DataLocation> functions, LocationData<CommandSourceStack> stack, @Nullable LocationData<NbtCompound> arguments);
}
