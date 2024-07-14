package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

@FunctionalInterface
public interface CodeFieldStorageMapper<T> {
    int accept(CodeFieldStorage fieldStorage, CodeWriter codeWriter, T value);
}
