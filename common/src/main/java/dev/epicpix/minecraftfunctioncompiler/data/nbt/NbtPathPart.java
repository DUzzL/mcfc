package dev.epicpix.minecraftfunctioncompiler.data.nbt;

public sealed interface NbtPathPart permits NbtPathPart.ArrayIndexCompound, NbtPathPart.ArrayIndexInteger, NbtPathPart.CompoundCheck, NbtPathPart.Named {

    record ArrayIndexInteger(int index) implements NbtPathPart {
    }

    record ArrayIndexCompound(NbtValue.Compound nbt) implements NbtPathPart {
    }

    record Named(String name) implements NbtPathPart {
    }

    record CompoundCheck(NbtValue.Compound nbt) implements NbtPathPart {
    }

}
