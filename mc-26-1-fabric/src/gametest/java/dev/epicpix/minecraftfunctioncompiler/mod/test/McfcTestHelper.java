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
            for(var entity : level.getAllEntities()) {
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
        return new ServerState(objectives);
    }

}
