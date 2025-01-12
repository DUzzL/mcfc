package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

@FunctionalInterface
public interface CodeFieldStorageMapper<T> {
    BytecodeValue accept(CodeFieldStorage fieldStorage, CodeWriter codeWriter, T value);
}
