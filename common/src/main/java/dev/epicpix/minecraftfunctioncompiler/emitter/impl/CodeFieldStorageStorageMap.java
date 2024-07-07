package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class CodeFieldStorageStorageMap {

    private final Map<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map;
    private final Map<CodeFieldStorageMapKind<?>, ArrayList<?>> storageMap = new HashMap<>();

    CodeFieldStorageStorageMap(Map<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> from) {
        this.map = Map.copyOf(from);
    }

    @SuppressWarnings("unchecked")
    public <T> CodeFieldStorageMapValue<T> getValue(CodeFieldStorageMapKind<T> key) {
        return (CodeFieldStorageMapValue<T>) map.get(key);
    }

    public <T> Class<?> getValueClass(CodeFieldStorageMapKind<T> key) {
        return getValue(key).clazz();
    }

    public <T> void writeCode(CodeFieldStorageMapKind<T> key, CodeFieldStorage fieldStorage, CodeWriter writer, T value) {
        getValue(key).mapper().accept(fieldStorage, writer, value);
    }

    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getStorage(CodeFieldStorageMapKind<T> key) {
        return (ArrayList<T>) storageMap.computeIfAbsent(key, k -> new ArrayList<>());
    }

    public <T> int checkIndex(CodeFieldStorageMapKind<T> key, T value) {
        return getStorage(key).indexOf(value);
    }

    public <T> void writeValue(CodeFieldStorageMapKind<T> key, T value) {
        var storage = getStorage(key);
        if(!storage.contains(value)) {
            storage.add(value);
        }
    }

}
