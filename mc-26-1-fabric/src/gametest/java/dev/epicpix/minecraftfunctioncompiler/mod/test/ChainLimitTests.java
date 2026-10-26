package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticKind;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * A function that calls itself twice never ends on its own. Vanilla stops the whole chain at the command limit.
 * Compiled code ignores the limit (documented) and hands deep recursion to vanilla, whose nested context then
 * stopped only itself: every compiled caller started the next exhausting call, which hung the server (VT Homes
 * `homes:bubble` next to BACAP). The chain must stop, the rest of the caller must be dropped like in vanilla, and
 * the function must stay compiled.
 */
public final class ChainLimitTests {
    private static final Identifier ID = Identifier.parse("mcfc-test:chain_limit");
    private static final Identifier RUN = Identifier.parse("mcfc-test:chain_limit/run");
    private static final Identifier LOOP = Identifier.parse("mcfc-test:chain_limit/loop");

    private ChainLimitTests() {}

    public static void registerFunction() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, ID, ChainLimitTests::awaitCompiler);
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
        helper.assertTrue(loader.hasFunctionLoaded(RUN) && loader.hasFunctionLoaded(LOOP), "Chain limit functions must be compiled");
        run(helper);
    }

    private static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var loader = FunctionCompilerMain.functionClassLoader;
        var rules = server.overworld().getGameRules();
        int previousLimit = (Integer) rules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH);
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.addObjective("mcfc_chain", ObjectiveCriteria.DUMMY, Component.literal("mcfc_chain"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        var function = server.getFunctions().get(RUN).orElseThrow();
        var after = ScoreHolder.forNameOnly("#after");
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule max_command_sequence_length 20000");
            for (boolean compiled : new boolean[]{false, true}) {
                if (compiled) loader.tryOn(); else loader.tryOff();
                scoreboard.resetSinglePlayerScore(after, objective);
                long start = System.nanoTime();
                server.getFunctions().execute(function, server.createCommandSourceStack());
                long millis = (System.nanoTime() - start) / 1_000_000;
                var mode = compiled ? "Compiled" : "Vanilla";
                helper.assertTrue(millis < 20_000, mode + " chain must stop at the command limit, took " + millis + " ms");
                helper.assertTrue(scoreboard.getPlayerScoreInfo(after, objective) == null, mode + " chain must drop the rest of the caller at the limit");
            }
            for (var diagnostic : loader.getDiagnostics()) {
                helper.assertTrue(diagnostic.kind() != DiagnosticKind.EXECUTION_ERROR, "Reaching the limit must not disable compiled code: " + diagnostic.message());
            }
        } finally {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule max_command_sequence_length " + previousLimit);
            loader.tryOn();
            scoreboard.removeObjective(objective);
        }
        helper.succeed();
    }
}
