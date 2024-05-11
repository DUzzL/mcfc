package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.loader.api.FabricLoader;

public enum McVersion {
    MC_1_20_1("1.20.1"),
    MC_1_20_2("1.20.2"),
    ;

    private final String name;
    McVersion(String name) {
        this.name = name;
    }

    public String getVersionName() {
        return name;
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
