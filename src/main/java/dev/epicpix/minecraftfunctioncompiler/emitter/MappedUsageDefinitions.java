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
import net.minecraft.world.scores.Team;

import java.util.*;

@SuppressWarnings("ALL")
public class MappedUsageDefinitions {

    private static void MinecraftServer_getScoreboard() { MappedUsageDefinitions.<MinecraftServer>type().getScoreboard(); }
    private static void MinecraftServer_getPlayerList() { MappedUsageDefinitions.<MinecraftServer>type().getPlayerList(); }
    private static void MinecraftServer_getAllLevels() { MappedUsageDefinitions.<MinecraftServer>type().getAllLevels(); }
    private static void ServerLevel_getAllEntities() { MappedUsageDefinitions.<ServerLevel>type().getAllEntities(); }

    private static void PlayerList_getPlayers() { MappedUsageDefinitions.<PlayerList>type().getPlayers(); }
    private static void PlayerList_getPlayerByName() { MappedUsageDefinitions.<PlayerList>type().getPlayerByName((String)null); }

    private static void Entity_getTags() { MappedUsageDefinitions.<Entity>type().getTags(); }
    private static void Entity_addTag() { MappedUsageDefinitions.<Entity>type().addTag((String)null); }
    private static void Entity_removeTag() { MappedUsageDefinitions.<Entity>type().removeTag((String)null); }
    private static void Entity_getScoreboardName() { MappedUsageDefinitions.<Entity>type().getScoreboardName(); }
    private static void Entity_kill() { MappedUsageDefinitions.<Entity>type().kill(); }
    private static void Entity_level() { MappedUsageDefinitions.<Entity>type().level(); }
    private static void Entity_position() { MappedUsageDefinitions.<Entity>type().position(); }
    private static void Entity_getRotationVector() { MappedUsageDefinitions.<Entity>type().getRotationVector(); }
    private static void Entity_getTeam() { MappedUsageDefinitions.<Entity>type().getTeam(); }
    private static void ServerPlayer_setGameMode() { MappedUsageDefinitions.<ServerPlayer>type().setGameMode((GameType)null); }
    private static void ServerPlayer_gameMode() { var unused = MappedUsageDefinitions.<ServerPlayer>type().gameMode; }
    private static void ServerPlayerGameMode_getGameModeForPlayer() { MappedUsageDefinitions.<ServerPlayerGameMode>type().getGameModeForPlayer(); }
    private static void Team_getName() { MappedUsageDefinitions.<Team>type().getName(); }

    private static void GameType_SURVIVAL() { var unused = GameType.SURVIVAL; }
    private static void GameType_CREATIVE() { var unused = GameType.CREATIVE; }
    private static void GameType_ADVENTURE() { var unused = GameType.ADVENTURE; }
    private static void GameType_SPECTATOR() { var unused = GameType.SPECTATOR; }

    private static void ServerLevel_getEntity() { MappedUsageDefinitions.<ServerLevel>type().getEntity((UUID)null); }
    private static void ServerFunctionManager_getLibrary() { var unused = MappedUsageDefinitions.<ServerFunctionManager>type().library; }
    private static void ServerFunctionManager_execute() throws FunctionInstantiationException { MappedUsageDefinitions.<ServerFunctionManager>type().execute((CommandFunction)null, (CommandSourceStack)null, (ServerFunctionManager.TraceCallbacks)null, (CompoundTag)null); }
    private static void ServerFunctionLibrary_getFunctions() { var unused = MappedUsageDefinitions.<ServerFunctionLibrary>type().functions; }

    private static void CommandSourceStack_getEntity() { MappedUsageDefinitions.<CommandSourceStack>type().getEntity(); }
    private static void CommandSourceStack_getServer() { MappedUsageDefinitions.<CommandSourceStack>type().getServer(); }
    private static void CommandSourceStack_withEntity() { MappedUsageDefinitions.<CommandSourceStack>type().withEntity((Entity)null); }
    private static void CommandSourceStack_withLevel() { MappedUsageDefinitions.<CommandSourceStack>type().withLevel((ServerLevel)null); }
    private static void CommandSourceStack_withPosition() { MappedUsageDefinitions.<CommandSourceStack>type().withPosition((Vec3)null); }
    private static void CommandSourceStack_withRotation() { MappedUsageDefinitions.<CommandSourceStack>type().withRotation((Vec2)null); }

    private static void Scoreboard_playerScores() { var unused = MappedUsageDefinitions.<Scoreboard>type().playerScores; }
    private static void Scoreboard_getObjective() { MappedUsageDefinitions.<Scoreboard>type().getObjective((String)null); }
    private static void Scoreboard_getOrCreatePlayerScore() { MappedUsageDefinitions.<Scoreboard>type().getOrCreatePlayerScore((String)null, (Objective)null); }
    private static void Scoreboard_resetPlayerScore() { MappedUsageDefinitions.<Scoreboard>type().resetPlayerScore((String)null, (Objective)null); }
    private static void Score_setScore() { MappedUsageDefinitions.<Score>type().setScore((int)0); }
    private static void Score_getScore() { MappedUsageDefinitions.<Score>type().getScore(); }

    private static void Vec3_x() { var unused = MappedUsageDefinitions.<Vec3>type().x; }
    private static void Vec3_y() { var unused = MappedUsageDefinitions.<Vec3>type().y; }
    private static void Vec3_z() { var unused = MappedUsageDefinitions.<Vec3>type().z; }

    private static void ArrayList_size() { MappedUsageDefinitions.<ArrayList<Object>>type().size(); }
    private static void ArrayList_get() { MappedUsageDefinitions.<ArrayList<Object>>type().get((int)0); }
    private static void List_size() { MappedUsageDefinitions.<List<Object>>type().size(); }
    private static void List_get() { MappedUsageDefinitions.<List<Object>>type().get((int)0); }
    private static void Set_size() { MappedUsageDefinitions.<Set<Object>>type().size(); }
    private static void Set_contains() { MappedUsageDefinitions.<Set<Object>>type().contains((Object)null); }
    private static void Map_keys() { MappedUsageDefinitions.<Map<Object, Object>>type().keySet(); }
    private static void Map_get() { MappedUsageDefinitions.<Map<Object, Object>>type().get((Object)null); }
    private static void Iterator_next() { MappedUsageDefinitions.<Iterator<Object>>type().next(); }
    private static void Iterator_hasNext() { MappedUsageDefinitions.<Iterator<Object>>type().hasNext(); }
    private static void Iterable_iterator() { MappedUsageDefinitions.<Iterable<Object>>type().iterator(); }

    private static void Math_floor() { Math.floor((double)0); }
    private static void Math_floorDiv() { Math.floorDiv((int)0, (int)0); }
    private static void Math_floorMod() { Math.floorMod((int)0, (int)0); }
    private static void Math_min() { Math.min((int)0, (int)0); }
    private static void Math_max() { Math.max((int)0, (int)0); }

    private static void String_equals() { MappedUsageDefinitions.<String>type().equals((String)null); }

    static <T> T type() { return null; }

}
