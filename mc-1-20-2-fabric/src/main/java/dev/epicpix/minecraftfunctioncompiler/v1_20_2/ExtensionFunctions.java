package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Used in bytecode instructions, to not insert too much code at once
@SuppressWarnings("unused")
public class ExtensionFunctions {

    public static Score getScore(ServerScoreboard scoreboard, String player, Objective objective) {
        Map<Objective, Score> map = scoreboard.playerScores.get(player);
        if (map == null) return null;
        return map.get(objective);
    }

    public static void sortEntitiesClosest(Vec3 origin, List<Entity> list) {
        list.sort(Comparator.comparingDouble(a -> a.distanceToSqr(origin)));
    }

    public static void sortEntitiesFurthest(Vec3 origin, List<Entity> list) {
        list.sort(Comparator.comparingDouble(a -> -a.distanceToSqr(origin)));
    }

}
