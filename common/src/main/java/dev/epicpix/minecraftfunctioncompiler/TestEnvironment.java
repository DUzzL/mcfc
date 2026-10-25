package dev.epicpix.minecraftfunctioncompiler;

/** Set by the game test entrypoint; compilation then blocks so tests run against compiled functions. */
public final class TestEnvironment {
    private TestEnvironment() {}

    private static volatile boolean isTesting = false;

    public static void markTestEnvironment() {
        isTesting = true;
    }

    public static boolean isTestEnvironment() {
        return isTesting;
    }
}
