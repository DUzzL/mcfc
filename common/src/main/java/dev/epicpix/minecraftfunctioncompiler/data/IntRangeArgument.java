package dev.epicpix.minecraftfunctioncompiler.data;

public record IntRangeArgument(
        Integer minimum,
        Integer maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }
}
