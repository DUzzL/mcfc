package dev.epicpix.minecraftfunctioncompiler.il;

import org.jetbrains.annotations.NotNull;

public record LabelData(int id) {
    public static LabelData ofId(int id) {
        return new LabelData(id);
    }

    @NotNull
    public String toString() {
        return "label<" + id + ">";
    }
}
