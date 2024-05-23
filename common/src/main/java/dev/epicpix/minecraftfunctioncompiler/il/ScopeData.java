package dev.epicpix.minecraftfunctioncompiler.il;

public record ScopeData(
        int id,
        boolean isSource,
        boolean isDestination
) {
    public static ScopeData ofId(int id) {
        return new ScopeData(id, false, false);
    }

    public ScopeData source() {
        return uses(true, false);
    }
    public ScopeData destination() {
        return uses(false, true);
    }
    public ScopeData uses(boolean source, boolean destination) {
        return new ScopeData(id, source, destination);
    }

    public String toString() {
        return (isSource ? "<in> " : "")
                + (isDestination ? "<out> " : "")
                + id;
    }
}
