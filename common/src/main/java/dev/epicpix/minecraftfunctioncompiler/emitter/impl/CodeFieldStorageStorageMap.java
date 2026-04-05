package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CodeFieldStorageStorageMap {

    private final Map<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map;

    // Per-kind: value -> stable insertion index (O(1) lookup, naturally thread-safe)
    private final ConcurrentHashMap<CodeFieldStorageMapKind<?>, ConcurrentHashMap<Object, Integer>> storageMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CodeFieldStorageMapKind<?>, AtomicInteger> counterMap = new ConcurrentHashMap<>();

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

    private ConcurrentHashMap<Object, Integer> getIndexMap(CodeFieldStorageMapKind<?> key) {
        return storageMap.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
    }

    private AtomicInteger getCounter(CodeFieldStorageMapKind<?> key) {
        return counterMap.computeIfAbsent(key, _ -> new AtomicInteger(0));
    }

    /** Returns the index of value in insertion order, or -1 if not present. O(1). */
    public <T> int checkIndex(CodeFieldStorageMapKind<T> key, T value) {
        return getIndexMap(key).getOrDefault(value, -1);
    }

    /** Registers value if not already present, assigning it the next stable index. O(1), lock-free. */
    public <T> void writeValue(CodeFieldStorageMapKind<T> key, T value) {
        var indexMap = getIndexMap(key);
        if(!indexMap.containsKey(value)) {
            // computeIfAbsent is atomic — only one thread assigns the index
            indexMap.computeIfAbsent(value, _ -> getCounter(key).getAndIncrement());
        }
    }

    /**
     * Returns values in stable insertion order for use during generate().
     * Called single-threaded after all parallel compilation is complete.
     */
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getStorage(CodeFieldStorageMapKind<T> key) {
        var indexMap = getIndexMap(key);
        var result = new ArrayList<T>(indexMap.size());
        // fill slots
        for(int i = 0; i < indexMap.size(); i++) result.add(null);
        for(var entry : indexMap.entrySet()) {
            result.set(entry.getValue(), (T) entry.getKey());
        }
        return result;
    }

}
