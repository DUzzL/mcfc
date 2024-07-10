package dev.epicpix.minecraftfunctioncompiler.il;

public record ScopeData(int id) {
    public static ScopeData ofId(int id) {
        return new ScopeData(id);
    }

    public String toString() {
        return "scope<" + id + ">";
    }
}
