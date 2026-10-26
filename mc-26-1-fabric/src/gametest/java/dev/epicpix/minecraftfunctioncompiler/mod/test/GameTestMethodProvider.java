package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.test.tests.Tests;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GameTestMethodProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameTestMethodProvider.class);

    @Nullable private static List<TestInfo> testInfos = null;

    public static List<TestInfo> getTests() {
        if(testInfos == null) return testInfos = discoverTests();
        return testInfos;
    }

    private static List<TestInfo> discoverTests() {
        LOGGER.info("Discovering tests");
        ArrayList<TestInfo> tests = new ArrayList<>();
        for(var test : Tests.TESTS) {
            tests.add(new TestInfo(test));
        }
        tests.add(new TestInfo(new ManyConstantsTest()));
        return tests;
    }
}
