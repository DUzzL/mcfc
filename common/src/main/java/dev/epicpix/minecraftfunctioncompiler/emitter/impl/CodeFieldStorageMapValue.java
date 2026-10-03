package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import org.jspecify.annotations.Nullable;

/** {@code elementAccessor}: for array-backed kinds, an optional static {@code (T[] values, int index) -> T} used to
 * read an element without a checkcast (otherwise the generic Object[] accessor plus a cast is emitted). */
public record CodeFieldStorageMapValue<T>(Class<?> type, CodeFieldStorageMapper<T> mapper, @Nullable InstructionTarget elementAccessor) {
    public CodeFieldStorageMapValue(Class<?> type, CodeFieldStorageMapper<T> mapper) {
        this(type, mapper, null);
    }
}
