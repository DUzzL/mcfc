package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callPattern_matcher;

public class CodeFieldStorageMap {

    private final HashMap<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map = new HashMap<>();

    public CodeFieldStorageMap addDefaults() {
        set(CodeFieldStorageMapKind.REGEX_PATTERN, new CodeFieldStorageMapValue<>(Pattern.class, (cf, cw, value) -> {
            cw.writeString(value);
            callPattern_matcher(cw);
            return 6;
        }));
        set(CodeFieldStorageMapKind.UUID, new CodeFieldStorageMapValue<>(UUID.class, (cf, cw, value) -> {
            cw.newObjectCreate(UUID.class);
            cw.writeLong(value.getMostSignificantBits());
            cw.writeLong(value.getLeastSignificantBits());
            cw.newObjectInit(UUID.class, long.class, long.class);
            return 13;
        }));
        return this;
    }

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
