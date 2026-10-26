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
 * A reward function started while a vanilla command runs is queued by vanilla and runs after that command
 * (Commands.executeCommandInContext with a current context). Compiled reward functions ran immediately, in the
 * middle of `advancement grant … from|everything`, so a reward that revokes a later advancement had no effect
 * (BACAP after `advancement grant @s everything`). Needs a player, so it cannot be a .mctest.
 */
public final class RewardOrderTests {
    private static final Identifier ID = Identifier.parse("mcfc-test:reward_order");
    private static final Identifier FUNCTION = Identifier.parse("mcfc-test:reward_order/revoke_b");
    private static final Identifier ADVANCEMENT_A = Identifier.parse("mcfc-test:reward_order/a");
    private static final Identifier ADVANCEMENT_B = Identifier.parse("mcfc-test:reward_order/b");

    private RewardOrderTests() {}

    public static void registerFunction() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, ID, RewardOrderTests::awaitCompiler);
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
        helper.assertTrue(loader.hasFunctionLoaded(FUNCTION), "Reward function must be compiled");
        run(helper);
    }

    private static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var loader = FunctionCompilerMain.functionClassLoader;
        var player = helper.makeMockServerPlayerInLevel();
        var a = server.getAdvancements().get(ADVANCEMENT_A);
        var b = server.getAdvancements().get(ADVANCEMENT_B);
        helper.assertTrue(a != null && b != null, "Reward order fixtures must load");
        try {
            for (boolean compiled : new boolean[]{false, true}) {
                if (compiled) loader.tryOn(); else loader.tryOff();
                player.getAdvancements().revoke(a, "never");
                player.getAdvancements().revoke(b, "never");
                // Grants a, then its child b; a's reward (revoke b) runs after the command, so b ends up revoked.
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "advancement grant " + player.getScoreboardName() + " from " + ADVANCEMENT_A);
                var mode = compiled ? "Compiled" : "Vanilla";
                helper.assertTrue(player.getAdvancements().getOrStartProgress(a).isDone(), mode + " grant must complete a");
                helper.assertTrue(!player.getAdvancements().getOrStartProgress(b).isDone(), mode + " reward of a must run after the command and revoke b");
            }
        } finally {
            loader.tryOn();
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
