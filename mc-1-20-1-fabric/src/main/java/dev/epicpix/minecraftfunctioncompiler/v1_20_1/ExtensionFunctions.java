package dev.epicpix.minecraftfunctioncompiler.v1_20_1;

import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    public static void executeFunction(ServerFunctionManager manager, CommandFunction commandFunction, CommandSourceStack commandSourceStack, @Nullable ServerFunctionManager.TraceCallbacks traceCallbacks) {
        if (manager.context != null) {
            if (traceCallbacks != null) {
                manager.context.reportError(Component.translatable("commands.debug.function.noRecursion").getString());
                return;
            }
            manager.context.delayFunctionCall(commandFunction, commandSourceStack);
            return;
        }
        try {
            manager.context = manager.new ExecutionContext(traceCallbacks);
            manager.context.runTopCommand(commandFunction, commandSourceStack);
        } finally {
            manager.context = null;
        }
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
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);

        float fx1 = nsinPitch * cosYaw;
        float fx3 = cosPitch * cosYaw;
        float fu1 = nsinPitch * sinYaw;
        float fu3 = cosPitch * sinYaw;

        float cx = sinYaw * fu3 + fx3 * cosYaw;
        float cy = fx3 * fu1 - fx1 * fu3;
        float cz = fx1 * cosYaw + sinYaw * fu1;

        double rx = fx1 * forwards + fu1 * up + cx * left;
        double ry = -sinYaw * forwards + cosYaw * up - cy * left;
        double rz = fx3 * forwards + fu3 * up - cz * left;
        return new Vec3(x + rx, y + ry, z + rz);
    }


    public static List<Entity> getEntitiesInside(ServerLevel level, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int limit) {
        var result = new ArrayList<Entity>();
        var sectionStorage = level.entityManager.sectionStorage;

        int chunkMinX = Mth.floor(minX - 2) >> 4;
        int chunkMinY = Mth.floor(minY - 4) >> 4;
        int chunkMinZ = Mth.floor(minZ - 2) >> 4;
        int chunkMaxX = Mth.floor(maxX + 2) >> 4;
        int chunkMaxY = Mth.floor(maxY) >> 4;
        int chunkMaxZ = Mth.floor(maxZ + 2) >> 4;

        for(var chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
            long chunksStart = SectionPos.asLong(chunkX, 0, 0);
            long chunksEnd = SectionPos.asLong(chunkX, -1, -1);

            var sectIter = sectionStorage.sectionIds.subSet(chunksStart, chunksEnd + 1L).iterator();
            while(sectIter.hasNext()) {
                var sectionId = sectIter.nextLong();
                int chunkY = SectionPos.y(sectionId);
                int chunkZ = SectionPos.z(sectionId);
                if (chunkY >= chunkMinY && chunkY <= chunkMaxY && chunkZ >= chunkMinZ && chunkZ <= chunkMaxZ) {
                    var section = sectionStorage.getSection(sectionId);
                    if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                        var entities = section.storage.find(Entity.class);
                        if(!entities.isEmpty()) {
                            for(var entity : entities) {
                                if(entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                                    result.add(entity);
                                    if(result.size() >= limit) return result;
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    public static MutableComponent formatEntityTextList(List<? extends Entity> entities, Component separator) {
        if(entities.isEmpty()) {
            return Component.empty();
        }else if (entities.size() == 1) {
            return entities.get(0).getDisplayName().copy();
        }else {
            MutableComponent result = Component.empty();
            boolean first = false;

            for(var entity : entities) {
                if(!first) result.append(separator);

                result.append(entity.getDisplayName());
                first = true;
            }

            return result;
        }
    }

}
