package dev.epicpix.minecraftfunctioncompiler.emitter;

import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;

@SuppressWarnings("ALL")
public class MappedUsageDefinitions {

    public static void MinecraftServer_getScoreboard() { MappedUsageDefinitions.<MinecraftServer>type().getScoreboard(); }
    public static void MinecraftServer_getPlayerList() { MappedUsageDefinitions.<MinecraftServer>type().getPlayerList(); }
    public static void MinecraftServer_getAllLevels() { MappedUsageDefinitions.<MinecraftServer>type().getAllLevels(); }
    public static void ServerLevel_getAllEntities() { MappedUsageDefinitions.<ServerLevel>type().getAllEntities(); }

    public static void PlayerList_getPlayers() { MappedUsageDefinitions.<PlayerList>type().getPlayers(); }
    public static void PlayerList_getPlayerByName() { MappedUsageDefinitions.<PlayerList>type().getPlayerByName((String)null); }

    public static void Entity_getTags() { MappedUsageDefinitions.<Entity>type().getTags(); }
    public static void Entity_addTag() { MappedUsageDefinitions.<Entity>type().addTag((String)null); }
    public static void Entity_removeTag() { MappedUsageDefinitions.<Entity>type().removeTag((String)null); }
    public static void Entity_getScoreboardName() { MappedUsageDefinitions.<Entity>type().getScoreboardName(); }
    public static void Entity_kill() { MappedUsageDefinitions.<Entity>type().kill(); }
    public static void Entity_level() { MappedUsageDefinitions.<Entity>type().level(); }
    public static void Entity_position() { MappedUsageDefinitions.<Entity>type().position(); }
    public static void Entity_getRotationVector() { MappedUsageDefinitions.<Entity>type().getRotationVector(); }
    public static void ServerPlayer_setGameMode() { MappedUsageDefinitions.<ServerPlayer>type().setGameMode((GameType)null); }
    public static void ServerPlayer_gameMode() { var unused = MappedUsageDefinitions.<ServerPlayer>type().gameMode; }
    public static void ServerPlayerGameMode_getGameModeForPlayer() { MappedUsageDefinitions.<ServerPlayerGameMode>type().getGameModeForPlayer(); }

    public static void GameType_SURVIVAL() { var unused = GameType.SURVIVAL; }
    public static void GameType_CREATIVE() { var unused = GameType.CREATIVE; }
    public static void GameType_ADVENTURE() { var unused = GameType.ADVENTURE; }
    public static void GameType_SPECTATOR() { var unused = GameType.SPECTATOR; }

    public static void ServerLevel_getEntity() { MappedUsageDefinitions.<ServerLevel>type().getEntity((UUID)null); }
    public static void ServerFunctionManager_getLibrary() { var unused = MappedUsageDefinitions.<ServerFunctionManager>type().library; }
    public static void ServerFunctionManager_execute() throws FunctionInstantiationException { MappedUsageDefinitions.<ServerFunctionManager>type().execute((CommandFunction)null, (CommandSourceStack)null, (ServerFunctionManager.TraceCallbacks)null, (CompoundTag)null); }
    public static void ServerFunctionLibrary_getFunctions() { var unused = MappedUsageDefinitions.<ServerFunctionLibrary>type().functions; }

    public static void CommandSourceStack_getEntity() { MappedUsageDefinitions.<CommandSourceStack>type().getEntity(); }
    public static void CommandSourceStack_getServer() { MappedUsageDefinitions.<CommandSourceStack>type().getServer(); }
    public static void CommandSourceStack_withEntity() { MappedUsageDefinitions.<CommandSourceStack>type().withEntity((Entity)null); }
    public static void CommandSourceStack_withLevel() { MappedUsageDefinitions.<CommandSourceStack>type().withLevel((ServerLevel)null); }
    public static void CommandSourceStack_withPosition() { MappedUsageDefinitions.<CommandSourceStack>type().withPosition((Vec3)null); }
    public static void CommandSourceStack_withRotation() { MappedUsageDefinitions.<CommandSourceStack>type().withRotation((Vec2)null); }

    public static void Scoreboard_playerScores() { var unused = MappedUsageDefinitions.<Scoreboard>type().playerScores; }
    public static void Scoreboard_getObjective() { MappedUsageDefinitions.<Scoreboard>type().getObjective((String)null); }
    public static void Scoreboard_getOrCreatePlayerScore() { MappedUsageDefinitions.<Scoreboard>type().getOrCreatePlayerScore((String)null, (Objective)null); }
    public static void Scoreboard_resetPlayerScore() { MappedUsageDefinitions.<Scoreboard>type().resetPlayerScore((String)null, (Objective)null); }
    public static void Score_setScore() { MappedUsageDefinitions.<Score>type().setScore((int)0); }
    public static void Score_getScore() { MappedUsageDefinitions.<Score>type().getScore(); }

    public static void Vec3_x() { var unused = MappedUsageDefinitions.<Vec3>type().x; }
    public static void Vec3_y() { var unused = MappedUsageDefinitions.<Vec3>type().y; }
    public static void Vec3_z() { var unused = MappedUsageDefinitions.<Vec3>type().z; }

    public static void ArrayList_size() { MappedUsageDefinitions.<ArrayList<Object>>type().size(); }
    public static void ArrayList_get() { MappedUsageDefinitions.<ArrayList<Object>>type().get((int)0); }
    public static void List_size() { MappedUsageDefinitions.<List<Object>>type().size(); }
    public static void List_get() { MappedUsageDefinitions.<List<Object>>type().get((int)0); }
    public static void Set_size() { MappedUsageDefinitions.<Set<Object>>type().size(); }
    public static void Set_contains() { MappedUsageDefinitions.<Set<Object>>type().contains((Object)null); }
    public static void Map_keys() { MappedUsageDefinitions.<Map<Object, Object>>type().keySet(); }
    public static void Map_get() { MappedUsageDefinitions.<Map<Object, Object>>type().get((Object)null); }
    public static void Iterator_next() { MappedUsageDefinitions.<Iterator<Object>>type().next(); }
    public static void Iterator_hasNext() { MappedUsageDefinitions.<Iterator<Object>>type().hasNext(); }
    public static void Iterable_iterator() { MappedUsageDefinitions.<Iterable<Object>>type().iterator(); }

    public static void Math_floor() { Math.floor((double)0); }
    public static void Math_floorDiv() { Math.floorDiv((int)0, (int)0); }
    public static void Math_floorMod() { Math.floorMod((int)0, (int)0); }
    public static void Math_min() { Math.min((int)0, (int)0); }
    public static void Math_max() { Math.max((int)0, (int)0); }

    static <T> T type() { return null; }

}
