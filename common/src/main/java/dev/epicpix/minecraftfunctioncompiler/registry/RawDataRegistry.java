package dev.epicpix.minecraftfunctioncompiler.registry;

import java.util.*;

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

    public Optional<T> get(K location) {
        var value = values.get(location);
        if(value == null) return Optional.empty();
        return Optional.of(value);
    }

    public void set(K location, T value) {
        if(frozen) {
            throw new IllegalStateException("Tried to modify a frozen registry");
        }
        values.put(location, value);
    }

    public Set<Map.Entry<K, T>> entrySet() {
        return Collections.unmodifiableSet(values.entrySet());
    }

}
