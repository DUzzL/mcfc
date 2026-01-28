package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.lang.constant.ClassDesc;
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

    public <T> ClassDesc getValueType(CodeFieldStorageMapKind<T> key) {
        return getValue(key).type().describeConstable().orElseThrow();
    }

    public <T> BytecodeValue writeCode(CodeFieldStorageMapKind<T> key, CodeFieldStorage fieldStorage, CodeWriter writer, T value) {
        return getValue(key).mapper().accept(fieldStorage, writer, value);
    }

    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getStorage(CodeFieldStorageMapKind<T> key) {
        return (ArrayList<T>) storageMap.computeIfAbsent(key, _ -> new ArrayList<>());
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
