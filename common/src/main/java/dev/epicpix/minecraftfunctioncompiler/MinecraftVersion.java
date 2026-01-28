package dev.epicpix.minecraftfunctioncompiler;

import org.jspecify.annotations.Nullable;

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

    @Nullable
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

    public static boolean isCurrentVersionInRange(@Nullable MinecraftVersion min, @Nullable MinecraftVersion max) {
        var currentVersion = getCurrentVersion();
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
        if(currentVersion == null) throw new RuntimeException("Current minecraft version is null");
        return currentVersion;
    }
}
