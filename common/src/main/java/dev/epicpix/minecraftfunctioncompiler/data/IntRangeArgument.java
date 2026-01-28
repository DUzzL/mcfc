package dev.epicpix.minecraftfunctioncompiler.data;

import org.jspecify.annotations.Nullable;

public record IntRangeArgument(
        @Nullable Integer minimum,
        @Nullable Integer maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }
}
