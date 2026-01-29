package dev.epicpix.minecraftfunctioncompiler.test;

import net.minecraft.resources.Identifier;
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
        tests.add(new TestInfo(Identifier.fromNamespaceAndPath("minecraftfunctioncompiler", "test1"), 1));
        tests.add(new TestInfo(Identifier.fromNamespaceAndPath("minecraftfunctioncompiler", "test2"), 1));
        tests.add(new TestInfo(Identifier.fromNamespaceAndPath("minecraftfunctioncompiler", "test3"), 1));
        tests.add(new TestInfo(Identifier.fromNamespaceAndPath("minecraftfunctioncompiler", "test4"), 1));
        tests.add(new TestInfo(Identifier.fromNamespaceAndPath("minecraftfunctioncompiler", "test5"), 1));
        return tests;
    }
}
