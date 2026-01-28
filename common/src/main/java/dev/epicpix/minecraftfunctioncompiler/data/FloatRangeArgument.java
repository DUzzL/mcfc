package dev.epicpix.minecraftfunctioncompiler.data;

import org.jspecify.annotations.Nullable;

public record FloatRangeArgument(
        @Nullable Float minimum,
        @Nullable Float maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }
}
