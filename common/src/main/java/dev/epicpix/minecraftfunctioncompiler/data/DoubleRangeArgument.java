package dev.epicpix.minecraftfunctioncompiler.data;

public record DoubleRangeArgument(
        Double minimum,
        Double maximum
) {
    public String toString() {
        return (minimum != null ? minimum : "") + ".." + (maximum != null ? maximum : "");
    }

    public DoubleRangeArgument square() {
        return new DoubleRangeArgument(minimum != null ? minimum * minimum : null, maximum != null ? maximum * maximum : null);
    }
}
