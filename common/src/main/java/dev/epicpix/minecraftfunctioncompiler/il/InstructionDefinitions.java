package dev.epicpix.minecraftfunctioncompiler.il;

import dev.epicpix.annotations.InstructionSourceClass;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCommandSourceStack;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCompoundTag;
import org.jspecify.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
@InstructionSourceClass(enumClass = "dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2", generatorClass = "dev.epicpix.minecraftfunctioncompiler.il.Instructions2")
interface InstructionDefinitions {
    // Minecraft specific instructions that have not been converted into data.instructions yet
    Instruction callFunctions(List<DataLocation> functions, LocationData<TCommandSourceStack> stack, @Nullable LocationData<TCompoundTag> arguments);
    Instruction callFunctionsWithResult(LocationData<Long> result, List<DataLocation> functions, LocationData<TCommandSourceStack> stack, @Nullable LocationData<TCompoundTag> arguments);
}
