package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.test.CompiledTest;
import dev.epicpix.minecraftfunctioncompiler.test.TestSetup;
import dev.epicpix.minecraftfunctioncompiler.test.state.ObjectiveState;
import dev.epicpix.minecraftfunctioncompiler.test.state.ServerState;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class McfcTestHelper {

    @Nullable public static CompiledTest currentTest;

    public static void loadTest(GameTestHelper helper, CompiledTest test) {
        var server = helper.getLevel().getServer();
        currentTest = test;
        server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    public static void clearServer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();

        for(var holder : server.getScoreboard().getTrackedPlayers()) server.getScoreboard().resetAllPlayerScores(holder);
        for(var objective : List.copyOf(server.getScoreboard().getObjectives())) server.getScoreboard().removeObjective(objective);

        for(var level : server.getAllLevels()) {
            // copy: remove() drops the entity from the live EntityLookup map being iterated
            for(var entity : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        server.setDifficulty(Difficulty.PEACEFUL, true);
    }

    public static void setupRun(GameTestHelper helper, CompiledTest test) {
        var server = helper.getLevel().getServer();

        test.setup(new TestSetup() {
            @Override
            public void createObjective(String name, String criterion) {
                server.getScoreboard().addObjective(name, ObjectiveCriteria.byName(criterion).orElseThrow(() -> helper.assertionException(Component.literal("criterion '" + criterion + "' does not exist"))), Component.literal(name), ObjectiveCriteria.RenderType.INTEGER, false, null);
            }
        });
    }

    public static ServerState takeServerState(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objectives = new HashMap<String, ObjectiveState>();
        for(var objective : scoreboard.getObjectives()) {
            var values = new Object2IntOpenHashMap<String>();
            for(var tracked : scoreboard.getTrackedPlayers()) {
                var scoreInfo = scoreboard.getPlayerScoreInfo(tracked, objective);
                if(scoreInfo != null) {
                    values.put(tracked.getScoreboardName(), scoreInfo.value());
                }
            }
            var objectiveState = new ObjectiveState(objective.getName(), values);
            objectives.put(objective.getName(), objectiveState);
        }
        var diagnostics = new java.util.ArrayList<String>();
        var runtimeErrors = new java.util.ArrayList<String>();
        var loader = dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain.functionClassLoader;
        if(loader != null) {
            for(var diagnostic : loader.getDiagnostics()) {
                diagnostics.add(diagnostic.source() + ": " + diagnostic.message());
                if(diagnostic.kind() == dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticKind.EXECUTION_ERROR) {
                    runtimeErrors.add(diagnostic.source() + ": " + (diagnostic.cause() != null ? diagnostic.cause() : diagnostic.message()));
                }
            }
        }
        return new ServerState(objectives, diagnostics, runtimeErrors);
    }

}
