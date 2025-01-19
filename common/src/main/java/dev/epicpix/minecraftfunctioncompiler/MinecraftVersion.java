package dev.epicpix.minecraftfunctioncompiler;

public enum MinecraftVersion {
    MC_1_19_4("1.19.4"),
    MC_1_20("1.20"),
    MC_1_20_1("1.20.1"),
    MC_1_20_2("1.20.2"),
    MC_1_20_3("1.20.3"),
    MC_1_20_4("1.20.4"),
    MC_1_20_5("1.20.5"),
    MC_1_20_6("1.20.6"),
    MC_1_21("1.21"),
    MC_1_21_1("1.21.1"),
    MC_1_21_2("1.21.2"),
    MC_1_21_3("1.21.3"),
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
            if(version.name.equals(ver)) {
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
