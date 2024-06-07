package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;

@SuppressWarnings("UnusedReturnValue")
public class ExtensionFunctions {

    public static ScoreAccess getScore(ServerScoreboard scoreboard, ScoreHolder player, Objective objective) {
        PlayerScores map = scoreboard.playerScores.get(player.getScoreboardName());
        if (map == null) return null;
        return map.get(objective) == null ? null : scoreboard.getOrCreatePlayerScore(player, objective);
    }

}
