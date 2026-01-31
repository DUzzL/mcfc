package dev.epicpix.minecraftfunctioncompiler.mod.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class McfcTestHelper {

    public static void setupRun(GameTestHelper helper, Identifier key) {
        var server = helper.getLevel().getServer();

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
