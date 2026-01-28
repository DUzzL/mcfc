package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callPattern_compile;

public class CodeFieldStorageMap {

    private final HashMap<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map = new HashMap<>();

    private static final InstructionTarget UUID_TARGET_INIT = InstructionTarget.FunctionTarget.initFunction(UUID.class, long.class, long.class);

    public CodeFieldStorageMap addDefaults() {
        set(CodeFieldStorageMapKind.REGEX_PATTERN, new CodeFieldStorageMapValue<>(Pattern.class, (cf, cw, value) -> callPattern_compile(cw, new BytecodeValue.StringValue(value))));
        set(CodeFieldStorageMapKind.UUID, new CodeFieldStorageMapValue<>(UUID.class, (cf, cw, value) -> cw.newObjectCreate(UUID_TARGET_INIT, new BytecodeValue.LongValue(value.getMostSignificantBits()), new BytecodeValue.LongValue(value.getLeastSignificantBits()))));
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
