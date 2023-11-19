package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.annotations.MapFieldUse
import dev.epicpix.annotations.MapFunctionUse
import dev.epicpix.annotations.MapSourceClass

@MapSourceClass("dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageInitializer")
object MappedUsageDefinitions {

    @MapFunctionUse("net/minecraft/server/MinecraftServer:getScoreboard()Lnet/minecraft/server/ServerScoreboard;")
    var MinecraftServer_getScoreboard: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/server/MinecraftServer:getPlayerList()Lnet/minecraft/server/players/PlayerList;")
    var MinecraftServer_getPlayerList: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/server/MinecraftServer:getAllLevels()Ljava/lang/Iterable;")
    var MinecraftServer_getAllLevels: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/server/level/ServerLevel:getAllEntities()Ljava/lang/Iterable;")
    var ServerLevel_getAllEntities: MappedFunctionData? = null

    @MapFunctionUse("net/minecraft/server/players/PlayerList:getPlayers()Ljava/util/List;")
    var PlayerList_getPlayers: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/server/players/PlayerList:getPlayerByName(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;")
    var PlayerList_getPlayerByName: MappedFunctionData? = null

    @MapFunctionUse("net/minecraft/world/entity/Entity:getTags()Ljava/util/Set;")
    var Entity_getTags: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:addTag(Ljava/lang/String;)Z")
    var Entity_addTag: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:removeTag(Ljava/lang/String;)Z")
    var Entity_removeTag: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:getScoreboardName()Ljava/lang/String;")
    var Entity_getScoreboardName: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:kill()V")
    var Entity_kill: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:level()Lnet/minecraft/world/level/Level;")
    var Entity_level: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:position()Lnet/minecraft/world/phys/Vec3;")
    var Entity_position: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:getRotationVector()Lnet/minecraft/world/phys/Vec2;")
    var Entity_getRotationVector: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/Entity:getTeam()Lnet/minecraft/world/scores/Team;")
    var Entity_getTeam: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/LivingEntity:getAttributes()Lnet/minecraft/world/entity/ai/attributes/AttributeMap;")
    var LivingEntity_getAttributes: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/server/level/ServerPlayer:setGameMode(Lnet/minecraft/world/level/GameType;)Z")
    var ServerPlayer_setGameMode: MappedFunctionData? = null
    @MapFieldUse("net/minecraft/server/level/ServerPlayer:gameMode:Lnet/minecraft/server/level/ServerPlayerGameMode;")
    var ServerPlayer_gameMode: MappedFieldData? = null
    @MapFunctionUse("net/minecraft/server/level/ServerPlayerGameMode:getGameModeForPlayer()Lnet/minecraft/world/level/GameType;")
    var ServerPlayerGameMode_getGameModeForPlayer: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/scores/Team:getName()Ljava/lang/String;")
    var Team_getName: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/ai/attributes/AttributeMap:getInstance(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;")
    var AttributeMap_getInstance: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/entity/ai/attributes/AttributeInstance:setBaseValue(D)V")
    var AttributeInstance_setBaseValue: MappedFunctionData? = null

    @MapFieldUse("net/minecraft/world/level/GameType:SURVIVAL:Lnet/minecraft/world/level/GameType;")
    var GameType_SURVIVAL: MappedFieldData? = null
    @MapFieldUse("net/minecraft/world/level/GameType:CREATIVE:Lnet/minecraft/world/level/GameType;")
    var GameType_CREATIVE: MappedFieldData? = null
    @MapFieldUse("net/minecraft/world/level/GameType:ADVENTURE:Lnet/minecraft/world/level/GameType;")
    var GameType_ADVENTURE: MappedFieldData? = null
    @MapFieldUse("net/minecraft/world/level/GameType:SPECTATOR:Lnet/minecraft/world/level/GameType;")
    var GameType_SPECTATOR: MappedFieldData? = null

    @MapFunctionUse("net/minecraft/server/level/ServerLevel:getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;")
    var ServerLevel_getEntity: MappedFunctionData? = null
    @MapFieldUse("net/minecraft/server/ServerFunctionManager:library:Lnet/minecraft/server/ServerFunctionLibrary;")
    var ServerFunctionManager_library: MappedFieldData? = null
    @MapFunctionUse("net/minecraft/server/ServerFunctionManager:execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager\$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I")
    var ServerFunctionManager_execute: MappedFunctionData? = null
    @MapFieldUse("net/minecraft/server/ServerFunctionLibrary:functions:Ljava/util/Map;")
    var ServerFunctionLibrary_functions: MappedFieldData? = null

    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:getEntity()Lnet/minecraft/world/entity/Entity;")
    var CommandSourceStack_getEntity: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:getServer()Lnet/minecraft/server/MinecraftServer;")
    var CommandSourceStack_getServer: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:withEntity(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
    var CommandSourceStack_withEntity: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:withLevel(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/commands/CommandSourceStack;")
    var CommandSourceStack_withLevel: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:withPosition(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/commands/CommandSourceStack;")
    var CommandSourceStack_withPosition: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/commands/CommandSourceStack:withRotation(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/commands/CommandSourceStack;")
    var CommandSourceStack_withRotation: MappedFunctionData? = null

    @MapFieldUse("net/minecraft/world/scores/Scoreboard:playerScores:Ljava/util/Map;")
    var Scoreboard_playerScores: MappedFieldData? = null
    @MapFunctionUse("net/minecraft/world/scores/Scoreboard:getObjective(Ljava/lang/String;)Lnet/minecraft/world/scores/Objective;")
    var Scoreboard_getObjective: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/scores/Scoreboard:getOrCreatePlayerScore(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
    var Scoreboard_getOrCreatePlayerScore: MappedFunctionData? = null
    @MapFunctionUse("dev/epicpix/minecraftfunctioncompiler/ExtensionFunctions:getScore(Lnet/minecraft/server/ServerScoreboard;Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/Score;")
    var Scoreboard_getScore: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/scores/Scoreboard:resetPlayerScore(Ljava/lang/String;Lnet/minecraft/world/scores/Objective;)V")
    var Scoreboard_resetPlayerScore: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/scores/Score:setScore(I)V")
    var Score_setScore: MappedFunctionData? = null
    @MapFunctionUse("net/minecraft/world/scores/Score:getScore()I")
    var Score_getScore: MappedFunctionData? = null

    @MapFieldUse("net/minecraft/core/registries/BuiltInRegistries:ATTRIBUTE:Lnet/minecraft/core/Registry;")
    var BuiltInRegistries_ATTRIBUTE: MappedFieldData? = null
    @MapFunctionUse("net/minecraft/core/Registry:get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;")
    var Registry_get: MappedFunctionData? = null

    @MapFieldUse("net/minecraft/world/phys/Vec3:x:D")
    var Vec3_x: MappedFieldData? = null
    @MapFieldUse("net/minecraft/world/phys/Vec3:y:D")
    var Vec3_y: MappedFieldData? = null
    @MapFieldUse("net/minecraft/world/phys/Vec3:z:D")
    var Vec3_z: MappedFieldData? = null

    init { MappedUsageInitializer.init() }
}