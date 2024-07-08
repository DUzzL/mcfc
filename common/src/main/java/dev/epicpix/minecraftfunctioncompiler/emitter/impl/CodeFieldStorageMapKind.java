package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;

import java.util.ArrayList;

public final class CodeFieldStorageMapKind<T> {

    private static final ArrayList<CodeFieldStorageMapKind<?>> VALUES = new ArrayList<>();

    private final String kind;

    private CodeFieldStorageMapKind(String kind) {
        this.kind = kind;
        VALUES.add(this);
    }

    public String getKind() {
        return kind;
    }

    public static final CodeFieldStorageMapKind<DataLocation> ATTRIBUTE = new CodeFieldStorageMapKind<>("ATTRIBUTE");
    public static final CodeFieldStorageMapKind<DataLocation> EFFECT = new CodeFieldStorageMapKind<>("EFFECT");
    public static final CodeFieldStorageMapKind<DataLocation> FUNCTION = new CodeFieldStorageMapKind<>("FUNCTION");
    public static final CodeFieldStorageMapKind<NbtValue> NBT_VALUE = new CodeFieldStorageMapKind<>("NBT_VALUE");
    public static final CodeFieldStorageMapKind<DataLocation> RESOURCE_LOCATION = new CodeFieldStorageMapKind<>("RESOURCE_LOCATION");

    @Override
    public String toString() {
        return getKind();
    }

    public static CodeFieldStorageMapKind<?>[] values() {
        return VALUES.toArray(new CodeFieldStorageMapKind<?>[0]);
    }
}
