package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.util.Mth;
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

    public static Vec3 calculateLocalCoordinates(float yaw, float pitch, double x, double y, double z, double left, double up, double forwards) {
        pitch *= (float) (Math.PI / 180.0f);
        yaw *= (float) (Math.PI / 180.0f);
        float nsinPitch = -Mth.sin(pitch);
        float cosPitch = Mth.cos(pitch);
        float cosYaw = Mth.cos(-yaw);
        float sinYaw = Mth.sin(-yaw);

        float fx1 = nsinPitch * cosYaw;
        float fx3 = cosPitch * cosYaw;
        float fu1 = nsinPitch * -sinYaw;
        float fu3 = cosPitch * -sinYaw;

        float cx = sinYaw * fu3 - fx3 * -cosYaw;
        float cy = fx3 * fu1 - fx1 * fu3;
        float cz = fx1 * -cosYaw - sinYaw * fu1;

        double rx = fx1 * forwards + fu1 * up - cx * left;
        double ry = sinYaw * forwards - cosYaw * up - cy * left;
        double rz = fx3 * forwards + fu3 * up - cz * left;
        return new Vec3(x + rx, y + ry, z + rz);
    }

}
