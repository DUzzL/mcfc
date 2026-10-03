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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ScoreboardSaveData;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * RT-26: a command whose single ScoreAccess creates a score must sync it (ServerScoreboard.onScoreChanged:
 * ClientboundSetScorePacket + setDirty) even when the new value is 0, e.g. `scoreboard players add <new> obj 0`.
 * Observed through the scoreboard's dirty flag, so it cannot be a .mctest.
 */
public final class ScoreSyncTests {
    private static final Identifier ID = Identifier.parse("mcfc-test:score_sync_new_zero");
    private static final String OBJECTIVE = "mcfc_rt26";
    private static final ScoreHolder NEW = ScoreHolder.forNameOnly("#rt26");
    private static final String[] FUNCTIONS = {"mcfc-test:rt26_add", "mcfc-test:rt26_remove", "mcfc-test:rt26_operation", "mcfc-test:rt26_store", "mcfc-test:rt26_entity_add"};

    private ScoreSyncTests() {}

    public static void registerFunction() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, ID, ScoreSyncTests::awaitCompiler);
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
        for (var function : FUNCTIONS) {
            helper.assertTrue(loader.hasFunctionLoaded(Identifier.parse(function)), "Function must be compiled: " + function);
        }
        run(helper);
    }

    private static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var loader = FunctionCompilerMain.functionClassLoader;
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal(OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#rt26_zero"), objective).set(0);
        var marker = helper.spawn(net.minecraft.world.entity.EntityTypes.MARKER, 0, 1, 0);
        marker.addTag("mcfc_rt26");
        try {
            for (var function : FUNCTIONS) {
                for (boolean compiled : new boolean[]{false, true}) {
                    if (compiled) loader.tryOn(); else loader.tryOff();
                    scoreboard.resetSinglePlayerScore(NEW, objective);
                    scoreboard.resetSinglePlayerScore(marker, objective);
                    scoreboard.storeToSaveDataIfDirty(new ScoreboardSaveData(ScoreboardSaveData.Packed.EMPTY));
                    run(server, function);
                    var probe = new ScoreboardSaveData(ScoreboardSaveData.Packed.EMPTY);
                    scoreboard.storeToSaveDataIfDirty(probe);
                    var mode = compiled ? "Compiled" : "Vanilla";
                    var holder = function.endsWith("entity_add") ? marker : NEW;
                    var created = scoreboard.getPlayerScoreInfo(holder, objective);
                    helper.assertTrue(created != null && created.value() == 0, mode + " " + function + " must create the score with value 0");
                    helper.assertTrue(probe.isDirty(), mode + " " + function + " must sync the newly created score");
                }
            }
        } finally {
            loader.tryOn();
            marker.discard();
            scoreboard.removeObjective(objective);
        }
        helper.succeed();
    }

    private static void run(MinecraftServer server, String function) {
        server.getFunctions().execute(server.getFunctions().get(Identifier.parse(function)).orElseThrow(), server.createCommandSourceStack());
    }
}
