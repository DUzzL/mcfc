package dev.epicpix.minecraftfunctioncompiler.il;

public record LocationData(
        Integer local,
        String string,
        Integer integer,
        boolean isSource,
        boolean isDestination
) {
    public static LocationData ofLocal(int local) {
        return new LocationData(local, null, null, false, false);
    }

    public static LocationData ofString(String string) {
        return new LocationData(null, string, null, false, false);
    }

    public static LocationData ofInteger(int integer) {
        return new LocationData(null, null, integer, false, false);
    }

    public LocationData source() {
        return uses(true, false);
    }
    public LocationData destination() {
        return uses(false, true);
    }
    public LocationData uses(boolean source, boolean destination) {
        return new LocationData(local, string, integer, source, destination);
    }

    public String toString() {
        return (local != null ? "local " : "")
                + (isSource ? "<in> " : "")
                + (isDestination ? "<out> " : "")
                + (local != null ? local : "")
                + (string != null ? string.replace("\\", "\\\\").replace("\n", "\\n") : "")
                + (integer != null ? integer : "");
    }
}