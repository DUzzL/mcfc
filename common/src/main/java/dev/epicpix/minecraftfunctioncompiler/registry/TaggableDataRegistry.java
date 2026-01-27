package dev.epicpix.minecraftfunctioncompiler.registry;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

import java.util.*;

public class TaggableDataRegistry<T> {

    private final DataRegistry<T> elements = new DataRegistry<>();
    private final DataRegistry<List<DataLocation>> tags = new DataRegistry<>();

    public void reset() {
        elements.reset();
        tags.reset();
    }

    public void freeze() {
        elements.freeze();
        tags.freeze();
    }

    public Optional<T> getElement(DataLocation location) {
        return elements.get(location);
    }

    public List<DataLocation> getTag(DataLocation location) {
        return tags.get(location).orElseGet(Collections::emptyList);
    }

    public void setElement(DataLocation location, T value) {
        elements.set(location, value);
    }

    public void setTag(DataLocation location, List<DataLocation> value) {
        tags.set(location, List.copyOf(value));
    }

    public Set<Map.Entry<DataLocation, T>> elementEntrySet() {
        return elements.entrySet();
    }

    public Set<Map.Entry<DataLocation, List<DataLocation>>> tagsEntrySet() {
        return tags.entrySet();
    }

}
