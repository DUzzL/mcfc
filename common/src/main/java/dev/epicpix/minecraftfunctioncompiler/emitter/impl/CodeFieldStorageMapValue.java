package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.util.TriConsumer;

public record CodeFieldStorageMapValue<T>(Class<?> clazz, TriConsumer<CodeFieldStorage, CodeWriter, T> mapper) {}
