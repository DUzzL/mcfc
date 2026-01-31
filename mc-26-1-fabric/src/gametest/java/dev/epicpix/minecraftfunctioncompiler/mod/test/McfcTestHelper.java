package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.test.CompiledTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class McfcTestHelper {

    @Nullable public static CompiledTest currentTest;

    public static void setupRun(GameTestHelper helper, CompiledTest test) {
        var server = helper.getLevel().getServer();

        currentTest = test;
        server.reloadResources(server.getPackRepository().getSelectedIds());

        for(var holder : server.getScoreboard().getTrackedPlayers()) server.getScoreboard().resetAllPlayerScores(holder);
        for(var objective : List.copyOf(server.getScoreboard().getObjectives())) server.getScoreboard().removeObjective(objective);

        for(var level : server.getAllLevels()) {
            for(var entity : level.getAllEntities()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        server.setDifficulty(Difficulty.PEACEFUL, true);
    }

}
