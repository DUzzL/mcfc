package dev.epicpix.minecraftfunctioncompiler.data;

public record DataLocation(String namespace, String path) {
    public DataLocation(String str) {
        this(str.indexOf(':') == -1 ? "minecraft" : str.substring(0, str.indexOf(':')), str.indexOf(':') == -1 ? str : str.substring(str.indexOf(':') + 1));
    }
    public String toString() {
        return namespace + ":" + path;
    }
}
