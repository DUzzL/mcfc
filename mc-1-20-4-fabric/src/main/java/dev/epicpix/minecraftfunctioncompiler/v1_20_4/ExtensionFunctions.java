package dev.epicpix.minecraftfunctioncompiler.v1_20_4;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

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

}
