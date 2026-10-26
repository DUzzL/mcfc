package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.test.CompiledTest;
import dev.epicpix.minecraftfunctioncompiler.test.ExceptionThrow;
import dev.epicpix.minecraftfunctioncompiler.test.TestFile;
import dev.epicpix.minecraftfunctioncompiler.test.TestSetup;
import dev.epicpix.minecraftfunctioncompiler.test.state.ServerState;

import java.util.ArrayList;
import java.util.List;

/**
 * Tens of thousands of distinct constants (here NBT compounds) used to overflow the constant pool of the single
 * generated data class ("65636 is not a valid index" from putstatic), which failed the whole load and left every
 * function on vanilla. Generated in code because the function text is too large for a .mctest file.
 */
public final class ManyConstantsTest implements CompiledTest {
    private static final int FUNCTIONS = 24;
    private static final int LINES_PER_FUNCTION = 1000;

    private static final List<TestFile> FILES = createFiles();

    private static List<TestFile> createFiles() {
        var files = new ArrayList<TestFile>();
        var run = new StringBuilder();
        for(int f = 0; f < FUNCTIONS; f++) {
            var body = new StringBuilder();
            for(int i = 0; i < LINES_PER_FUNCTION; i++) {
                int value = f * LINES_PER_FUNCTION + i;
                body.append("data modify storage mcfc-test:many v").append(value).append(" set value {i:").append(value).append("}\n");
            }
            files.add(new TestFile(new DataLocation("mcfc-test", "function/part" + f + ".mcfunction"), body.toString()));
            run.append("function mcfc-test:part").append(f).append('\n');
        }
        int last = FUNCTIONS * LINES_PER_FUNCTION - 1;
        run.append("execute store result score last t run data get storage mcfc-test:many v").append(last).append(".i\n");
        run.append("data remove storage mcfc-test:many v0\n");
        files.add(new TestFile(new DataLocation("mcfc-test", "function/run.mcfunction"), run.toString()));
        return List.copyOf(files);
    }

    @Override
    public DataLocation id() {
        return new DataLocation("mcfc-test", "data_class/many_constants");
    }

    @Override
    public List<TestFile> files() {
        return FILES;
    }

    @Override
    public void setup(TestSetup setup) {
        setup.createObjective("t", "dummy");
    }

    @Override
    public void validate(ServerState state, ExceptionThrow thrower) {
        state.assertObjectiveExists(thrower, "t").assertScoreSet(thrower, "last", FUNCTIONS * LINES_PER_FUNCTION - 1);
    }
}
