package dev.epicpix.minecraftfunctioncompiler.registry;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DataRegistry<T> {
    private boolean frozen = false;
    private Map<DataLocation, T> values = new HashMap<>();

    public void reset() {
        frozen = false;
        values = new HashMap<>();
    }

    public void freeze() {
        if(frozen) {
            throw new IllegalStateException("Tried to freeze a frozen registry");
        }
        frozen = true;
        values = Map.copyOf(values);
    }

    public T get(DataLocation location) {
        return values.get(location);
    }

    public void set(DataLocation location, T value) {
        if(frozen) {
            throw new IllegalStateException("Tried to modify a frozen registry");
        }
        values.put(location, value);
    }
}
