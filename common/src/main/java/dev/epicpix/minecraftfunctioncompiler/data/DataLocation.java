package dev.epicpix.minecraftfunctioncompiler.data;

public record DataLocation(String namespace, String path) {
    public String toString() {
        return namespace + ":" + path;
    }
}
