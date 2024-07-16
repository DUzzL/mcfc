package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import java.util.HashMap;

public class CodeFieldStorageMap {

    private final HashMap<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map = new HashMap<>();

    public CodeFieldStorageStorageMap toStorageMap() {
        return new CodeFieldStorageStorageMap(map);
    }

    public <T> void set(CodeFieldStorageMapKind<T> key, CodeFieldStorageMapValue<T> value) {
        map.put(key, value);
    }

    public void checkEntryCount() {
        if(map.size() != CodeFieldStorageMapKind.values().length) {
            throw new IllegalArgumentException("CodeFieldStorageMap does not map every kind value");
        }
    }

}
