package dev.epicpix.minecraftfunctioncompiler.test;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;

import java.util.List;

public interface CompiledTest {
    DataLocation id();
    List<TestFile> files();
}
