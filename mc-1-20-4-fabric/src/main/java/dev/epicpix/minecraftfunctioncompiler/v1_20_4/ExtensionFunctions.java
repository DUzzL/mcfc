package dev.epicpix.minecraftfunctioncompiler.v1_20_4;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Pattern;

// Used in bytecode instructions, to not insert too much code at once
@SuppressWarnings("unused")
public class ExtensionFunctions {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ScoreAccess getScore(ServerScoreboard scoreboard, ScoreHolder player, Objective objective) {
        PlayerScores map = scoreboard.playerScores.get(player.getScoreboardName());
        if (map == null) return null;
        return map.get(objective) == null ? null : scoreboard.getOrCreatePlayerScore(player, objective);
    }

    public static void executeFunction(ServerFunctionManager manager, CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CompoundTag arguments) {
        ProfilerFiller profilerFiller = manager.server.getProfiler();
        profilerFiller.push(() -> "function " + commandFunction.id());

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedFunction = commandFunction.instantiate(arguments, manager.getDispatcher(), commandSourceStack);
            Commands.executeCommandInContext(
                    commandSourceStack,
                    executionContext -> ExecutionContext.queueInitialFunctionCall(executionContext, instantiatedFunction, commandSourceStack, CommandResultCallback.EMPTY)
            );
        } catch (FunctionInstantiationException ignored) {
        } catch (Exception e) {
            LOGGER.warn("Failed to execute function {}", commandFunction.id(), e);
        } finally {
            profilerFiller.pop();
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

    public static String stringifyTag(Tag tag) {
        if(tag instanceof ByteTag cTag) {
            return Byte.toString(cTag.getAsByte());
        }else if(tag instanceof ShortTag cTag) {
            return Short.toString(cTag.getAsShort());
        }else if(tag instanceof IntTag cTag) {
            return Integer.toString(cTag.getAsInt());
        }else if(tag instanceof LongTag cTag) {
            return Long.toString(cTag.getAsLong());
        }else if(tag instanceof FloatTag cTag) {
            return TAG_DECIMAL_FORMAT.format(cTag.getAsFloat());
        }else if(tag instanceof DoubleTag cTag) {
            return TAG_DECIMAL_FORMAT.format(cTag.getAsDouble());
        }else if(tag instanceof StringTag cTag) {
            return cTag.getAsString();
        }else {
            var builder = new StringBuilder();
            appendWithUnitsTag(builder, tag);
            return builder.toString();
        }
    }

    private static void appendWithUnitsTag(StringBuilder builder, Tag tag) {
        if(tag instanceof ByteTag cTag) {
            builder.append(cTag.getAsByte());
            builder.append('b');
        }else if(tag instanceof ShortTag cTag) {
            builder.append(cTag.getAsShort());
            builder.append('s');
        }else if(tag instanceof IntTag cTag) {
            builder.append(cTag.getAsInt());
        }else if(tag instanceof LongTag cTag) {
            builder.append(cTag.getAsLong());
            builder.append('L');
        }else if(tag instanceof FloatTag cTag) {
            builder.append(cTag.getAsFloat());
            builder.append('f');
        }else if(tag instanceof DoubleTag cTag) {
            builder.append(cTag.getAsDouble());
            builder.append('d');
        }else if(tag instanceof StringTag cTag) {
            builder.append(StringTag.quoteAndEscape(cTag.getAsString()));
        }else if(tag instanceof ByteArrayTag cTag) {
            builder.append("[B;");
            var arr = cTag.getAsByteArray();
            for(int i = 0; i < arr.length; i++) {
                if(i != 0) builder.append(',');
                builder.append(arr[i]).append('B');
            }
            builder.append(']');
        }else if(tag instanceof IntArrayTag cTag) {
            builder.append("[I;");
            var arr = cTag.getAsIntArray();
            for(int i = 0; i < arr.length; i++) {
                if(i != 0) builder.append(',');
                builder.append(arr[i]);
            }
            builder.append(']');
        }else if(tag instanceof LongArrayTag cTag) {
            builder.append("[L;");
            var arr = cTag.getAsLongArray();
            for(int i = 0; i < arr.length; i++) {
                if(i != 0) builder.append(',');
                builder.append(arr[i]).append('L');
            }
            builder.append(']');
        }else if(tag instanceof ListTag cTag) {
            builder.append("[");
            for(int i = 0; i < cTag.size(); i++) {
                if(i != 0) builder.append(',');
                appendWithUnitsTag(builder, cTag.get(i));
            }
            builder.append(']');
        }else if(tag instanceof CompoundTag cTag) {
            builder.append('{');
            var list = new ArrayList<>(cTag.getAllKeys());
            Collections.sort(list);

            for(int i = 0; i<list.size(); i++) {
                if(i != 0) builder.append(',');

                appendOptionalStringEscape(builder, list.get(i));
                builder.append(':');
                appendWithUnitsTag(builder, cTag.get(list.get(i)));
            }

            builder.append('}');
        }else {
            builder.append(new StringTagVisitor().visit(tag));
        }
    }

    private static void appendOptionalStringEscape(StringBuilder builder, String string) {
        if(STRING_SIMPLE_VALUE.matcher(string).matches()) {
            builder.append(string);
        }else {
            builder.append(StringTag.quoteAndEscape(string));
        }
    }

    private static final Pattern STRING_SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final DecimalFormat TAG_DECIMAL_FORMAT = Util.make(new DecimalFormat("#"), format -> {
        format.setMaximumFractionDigits(15);
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });


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

}
