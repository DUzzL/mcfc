package dev.epicpix.minecraftfunctioncompiler.test;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.test.state.ServerState;

import java.util.List;

public interface CompiledTest {
    DataLocation id();
    List<TestFile> files();

    void setup(TestSetup setup);
    void validate(ServerState state, ExceptionThrow thrower);
}
