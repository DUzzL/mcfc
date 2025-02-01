package dev.epicpix.minecraftfunctioncompiler.data;

public interface NbtDataLocation {

    record Block(WorldCoordinates position) implements NbtDataLocation {}
    record Entity(SelectorArgument selector) implements NbtDataLocation {}
    record Storage(DataLocation storage) implements NbtDataLocation {}

}
