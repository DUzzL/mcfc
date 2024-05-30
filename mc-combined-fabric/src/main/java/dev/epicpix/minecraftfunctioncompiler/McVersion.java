package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.loader.api.FabricLoader;

public enum McVersion {
    MC_1_20("1.20", "1.20.1"),
    MC_1_20_1("1.20.1"),
    MC_1_20_2("1.20.2"),
    MC_1_20_3("1.20.3", "1.20.4"),
    MC_1_20_4("1.20.4"),
    ;

    private final String name;
    private final String mixinVersion;
    McVersion(String name, String mixinVersion) {
        this.name = name;
        this.mixinVersion = mixinVersion;
    }
    McVersion(String name) {
        this(name, name);
    }

    public String getMixinVersionName() {
        return mixinVersion;
    }

    public static McVersion getVersion() {
        String ver = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString();
        for(var version : values()) {
            if(version.name.equals(ver)) {
                return version;
            }
        }
        throw new RuntimeException("Unrecognized minecraft version: " + ver);
    }

}
