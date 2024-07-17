package dev.epicpix.minecraftfunctioncompiler.registry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RawDataRegistry<K, T> {
    private boolean frozen = false;
    private Map<K, T> values = new HashMap<>();

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

    public T get(K location) {
        return values.get(location);
    }

    public void set(K location, T value) {
        if(frozen) {
            throw new IllegalStateException("Tried to modify a frozen registry");
        }
        values.put(location, value);
    }

    public Set<Map.Entry<K, T>> entrySet() {
        return new HashSet<>(values.entrySet());
    }

}
