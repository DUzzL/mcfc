package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.lang.constant.ClassDesc;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CodeFieldStorageStorageMap {

    private final Map<CodeFieldStorageMapKind<?>, CodeFieldStorageMapValue<?>> map;

    // Per-kind: value -> stable insertion index, and index -> value (O(1) both ways, naturally thread-safe)
    private final ConcurrentHashMap<CodeFieldStorageMapKind<?>, ConcurrentHashMap<Object, Integer>> storageMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CodeFieldStorageMapKind<?>, ConcurrentHashMap<Integer, Object>> reverseMap = new ConcurrentHashMap<>();
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

    private ConcurrentHashMap<Integer, Object> getReverseMap(CodeFieldStorageMapKind<?> key) {
        return reverseMap.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
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
            indexMap.computeIfAbsent(value, _ -> {
                int index = getCounter(key).getAndIncrement();
                getReverseMap(key).put(index, value);
                return index;
            });
        }
    }

    /** Number of registered values of this kind. Called single-threaded during generate(). */
    public int storageSize(CodeFieldStorageMapKind<?> key) {
        return getIndexMap(key).size();
    }

    /** Value with insertion index {@code index}. Called single-threaded during generate(). */
    @SuppressWarnings("unchecked")
    public <T> T valueAt(CodeFieldStorageMapKind<T> key, int index) {
        return (T) java.util.Objects.requireNonNull(getReverseMap(key).get(index));
    }

}
