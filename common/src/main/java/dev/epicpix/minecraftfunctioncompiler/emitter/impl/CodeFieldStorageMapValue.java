package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import org.objectweb.asm.Type;

public record CodeFieldStorageMapValue<T>(Type type, CodeFieldStorageMapper<T> mapper) {}
