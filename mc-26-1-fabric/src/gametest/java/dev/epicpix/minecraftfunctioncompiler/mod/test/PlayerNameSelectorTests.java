package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Player-name entity targets are read with brigadier readString (EntitySelectorParser.parseNameOrUUID):
 * `tag "test-mock-player" add …` targets the player without the quotes. Needs a real player, so it
 * cannot be a .mctest.
 */
public final class PlayerNameSelectorTests {
    private static final Identifier ID = Identifier.parse("mcfc-test:player_name_selector");
    private static final Identifier FUNCTION = Identifier.parse("mcfc-test:quoted_player_name");
    private static final String[] TAGS = {"mcfc_quoted", "mcfc_unquoted"};

    private PlayerNameSelectorTests() {}

    public static void registerFunction() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, ID, PlayerNameSelectorTests::awaitCompiler);
    }

    public static void registerInstance(Registry<GameTestInstance> instances, Registry<TestEnvironmentDefinition<?>> environments) {
        Registry.register(environments, ID, new TestEnvironmentDefinition.AllOf());
        var environment = environments.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, ID));
        Registry.register(instances, ID, new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, ID),
                new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 1200, 1, true)));
    }

    private static void awaitCompiler(GameTestHelper helper) {
        var loader = FunctionCompilerMain.functionClassLoader;
        if (loader != null && loader.getState() == dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader.State.READY_OFF) {
            loader.tryOn();
        }
        if (loader == null || !loader.isUseReady()) {
            helper.runAfterDelay(1, () -> awaitCompiler(helper));
            return;
        }
        helper.assertTrue(loader.hasFunctionLoaded(FUNCTION), "Quoted player name function must be compiled");
        run(helper);
    }

    private static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var loader = FunctionCompilerMain.functionClassLoader;
        var player = helper.makeMockServerPlayerInLevel();
        var function = server.getFunctions().get(FUNCTION).orElseThrow();
        try {
            for (boolean compiled : new boolean[]{false, true}) {
                if (compiled) loader.tryOn(); else loader.tryOff();
                for (var tag : TAGS) player.removeTag(tag);
                server.getFunctions().execute(function, server.createCommandSourceStack());
                for (var tag : TAGS) {
                    helper.assertTrue(player.entityTags().contains(tag),
                            (compiled ? "Compiled" : "Vanilla") + " must tag the player named by the selector: " + tag);
                }
            }
        } finally {
            loader.tryOn();
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
