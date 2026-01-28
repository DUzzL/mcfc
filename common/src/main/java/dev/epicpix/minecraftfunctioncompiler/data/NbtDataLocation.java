package dev.epicpix.minecraftfunctioncompiler.data;

public sealed interface NbtDataLocation permits NbtDataLocation.Block, NbtDataLocation.Entity, NbtDataLocation.Storage {

    record Block(WorldCoordinates position) implements NbtDataLocation {}
    record Entity(SelectorArgument selector) implements NbtDataLocation {}
    record Storage(DataLocation storage) implements NbtDataLocation {}

}
