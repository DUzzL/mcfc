package dev.epicpix.minecraftfunctioncompiler;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;

import java.util.Map;

@SuppressWarnings("UnusedReturnValue")
public class ExtensionFunctions {

    public static Score getScore(ServerScoreboard scoreboard, String player, Objective objective) {
        Map<Objective, Score> map = scoreboard.playerScores.get(player);
        if (map == null) return null;
        return map.get(objective);
    }

}
