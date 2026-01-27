package dev.epicpix.minecraftfunctioncompiler;

public enum MinecraftVersion {
    MC_26_1("26.1"),
    ;

    private final String name;
    MinecraftVersion(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static MinecraftVersion currentVersion;

    public static MinecraftVersion getByName(String ver) {
        for(var version : values()) {
            if(ver.startsWith(version.getName())) {
                return version;
            }
        }
        throw new IllegalArgumentException("Cannot find supported minecraft version from string: \"" + ver + "\"");
    }

    public static void setCurrentVersion(MinecraftVersion currentVersion) {
        if(MinecraftVersion.currentVersion == null) {
            MinecraftVersion.currentVersion = currentVersion;
        }
    }

    public static boolean isCurrentVersionInRange(MinecraftVersion min, MinecraftVersion max) {
        if(min != null) {
            if(currentVersion.ordinal() < min.ordinal()) {
                return false;
            }
        }
        if(max != null) {
            return currentVersion.ordinal() <= max.ordinal();
        }
        return true;
    }

    public static MinecraftVersion getCurrentVersion() {
        return currentVersion;
    }
}
