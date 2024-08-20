package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;

import java.util.ArrayList;
import java.util.regex.Pattern;

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
    public static final CodeFieldStorageMapKind<NbtValue.Compound> NBT_COMPOUND = new CodeFieldStorageMapKind<>("NBT_COMPOUND");
    public static final CodeFieldStorageMapKind<NbtValue> NBT_VALUE = new CodeFieldStorageMapKind<>("NBT_VALUE");
    public static final CodeFieldStorageMapKind<String> REGEX_PATTERN = new CodeFieldStorageMapKind<>("REGEX_PATTERN");
    public static final CodeFieldStorageMapKind<DataLocation> RESOURCE_LOCATION = new CodeFieldStorageMapKind<>("RESOURCE_LOCATION");
    public static final CodeFieldStorageMapKind<String> SCOREBOARD_CRITERION = new CodeFieldStorageMapKind<>("SCOREBOARD_CRITERION");
    public static final CodeFieldStorageMapKind<Text> TEXT = new CodeFieldStorageMapKind<>("TEXT");
    public static final CodeFieldStorageMapKind<TextStyle> TEXT_STYLE = new CodeFieldStorageMapKind<>("TEXT_STYLE");

    @Override
    public String toString() {
        return getKind();
    }

    public static CodeFieldStorageMapKind<?>[] values() {
        return VALUES.toArray(new CodeFieldStorageMapKind<?>[0]);
    }
}
