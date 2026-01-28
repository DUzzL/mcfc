package dev.epicpix.minecraftfunctioncompiler.data;

import org.jspecify.annotations.Nullable;

public record DoubleRangeArgument(
        @Nullable Double minimum,
        @Nullable Double maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }

    public DoubleRangeArgument square() {
        return new DoubleRangeArgument(minimum != null ? minimum * minimum : null, maximum != null ? maximum * maximum : null);
    }
}
