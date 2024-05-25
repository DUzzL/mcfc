package dev.epicpix.minecraftfunctioncompiler.data;

public record FloatRangeArgument(
        Float minimum,
        Float maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }
}
