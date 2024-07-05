package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.util.TriConsumer;

public record CodeFieldStorageMapValue(Class<?> clazz, TriConsumer<CodeFieldStorage, CodeWriter, DataLocation> mapper) {}
