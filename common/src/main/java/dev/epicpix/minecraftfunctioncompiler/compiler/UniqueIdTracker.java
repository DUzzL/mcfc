package dev.epicpix.minecraftfunctioncompiler.compiler;

public final class UniqueIdTracker {
    private int current = 0;

    public int createId() {
        return ++current;
    }

    public String toString() {
        return "UniqueIdTracker{current=" + current + "}";
    }
}
