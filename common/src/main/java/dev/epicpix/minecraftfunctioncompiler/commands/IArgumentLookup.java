package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation;

import java.util.List;

public interface IArgumentLookup {
    List<DataLocation> lookupFunctions(TaggableLocation functions);
}
