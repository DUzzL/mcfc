package dev.epicpix.minecraftfunctioncompiler.mod;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.StringTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

// Used in bytecode instructions, to not insert too much code at once
@SuppressWarnings("unused")
public class ExtensionFunctions {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static @Nullable ScoreAccess getScore(ServerScoreboard scoreboard, ScoreHolder player, Objective objective) {
        PlayerScores map = scoreboard.playerScores.get(player.getScoreboardName());
        if (map == null) return null;
        return map.get(objective) == null ? null : scoreboard.getOrCreatePlayerScore(player, objective);
    }

    // Vanilla's /scoreboard players set|add|remove|operation on a read-only criterion
    // (deathCount, playerKillCount, custom:*, etc.) fails with a command syntax error,
    // leaving the score untouched. Mirror that: no-op instead of letting ScoreAccess.set
    // throw IllegalStateException and kill the whole compiled function.
    public static void setScoreSafe(ServerScoreboard scoreboard, Objective objective, ScoreHolder holder, int value) {
        if (objective.getCriteria().isReadOnly()) return;
        scoreboard.getOrCreatePlayerScore(holder, objective).set(value);
    }

    public static void executeFunction(ServerFunctionManager manager, CommandFunction<CommandSourceStack> commandFunction, CommandSourceStack commandSourceStack, CompoundTag arguments) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push(() -> "function " + commandFunction.id());

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedFunction = commandFunction.instantiate(arguments, manager.getDispatcher());
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

    // Tag creation helpers for execute store byte/short/long
    public static Tag createByteTag(int value) { return ByteTag.valueOf((byte) value); }
    public static Tag createShortTag(int value) { return ShortTag.valueOf((short) value); }
    public static Tag createLongTag(int value) { return LongTag.valueOf(value); }
    public static Tag createLongTagFromDouble(double value) { return LongTag.valueOf((long) value); }

    // -------------------------------------------------------------------------
    // Effect commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void effectGive(Entity entity, @Nullable Entity sourceEntity, MinecraftServer server, String effectId, int durationTicks, int amplifier, boolean hideParticles) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return;
        var effectKey = ResourceKey.create(Registries.MOB_EFFECT, cachedId(effectId));
        server.registryAccess().lookup(Registries.MOB_EFFECT)
            .flatMap(lookup -> lookup.get(effectKey))
            .ifPresent(effect -> living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, durationTicks, amplifier, false, !hideParticles), sourceEntity));
    }

    public static void effectClearAll(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.removeAllEffects();
        }
    }

    public static void effectClearEffect(Entity entity, MinecraftServer server, String effectId) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return;
        var effectKey = ResourceKey.create(Registries.MOB_EFFECT, cachedId(effectId));
        server.registryAccess().lookup(Registries.MOB_EFFECT)
            .flatMap(lookup -> lookup.get(effectKey))
            .ifPresent(living::removeEffect);
    }

    // -------------------------------------------------------------------------
    // Teleport commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void teleportToEntity(Entity target, Entity destination) {
        if (!(destination.level() instanceof ServerLevel destLevel)) return;
        if (target.teleportTo(destLevel, destination.getX(), destination.getY(), destination.getZ(),
                java.util.Set.of(), destination.getYRot(), destination.getXRot(), true)) {
            postTeleportCleanup(target);
        }
    }

    public static void teleportToPos(Entity target, ServerLevel world, Vec3 pos) {
        if (target.teleportTo(world, pos.x, pos.y, pos.z, java.util.Set.of(), target.getYRot(), target.getXRot(), true)) {
            postTeleportCleanup(target);
        }
    }

    public static void teleportToPosRot(Entity target, ServerLevel world, Vec3 pos, float yaw, float pitch) {
        if (target.teleportTo(world, pos.x, pos.y, pos.z, java.util.Set.of(), Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch), true)) {
            postTeleportCleanup(target);
        }
    }

    private static void postTeleportCleanup(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living && living.isFallFlying())) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            entity.setOnGround(true);
        }
        if (entity instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.getNavigation().stop();
        }
    }

    // -------------------------------------------------------------------------
    // Clear commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static int clearPlayerInventory(ServerPlayer player) {
        int count = player.getInventory().clearOrCountMatchingItems(stack -> true, -1, player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.slotsChanged(player.getInventory());
        return count;
    }

    public static int clearPlayerItem(ServerPlayer player, MinecraftServer server, String itemPredicate, int maxCount) {
        var parsed = parseItemPredicate(server, itemPredicate);
        if (parsed == null) return 0;
        int count = player.getInventory().clearOrCountMatchingItems(parsed, maxCount, player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.slotsChanged(player.getInventory());
        return count;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.function.Predicate<net.minecraft.world.item.ItemStack>> ITEM_PREDICATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.util.function.@Nullable Predicate<net.minecraft.world.item.ItemStack> parseItemPredicate(MinecraftServer server, String itemString) {
        var cached = ITEM_PREDICATE_CACHE.get(itemString);
        if (cached != null) return cached;
        try {
            var input = parseItemInput(server, itemString);
            if (input == null) return null;
            var holder = input.item();
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> predicate = stack -> stack.is(holder);
            ITEM_PREDICATE_CACHE.put(itemString, predicate);
            return predicate;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse item predicate '{}': {}", itemString, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Stopsound commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void stopsoundAll(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
    }

    public static void stopsoundSource(ServerPlayer player, String source) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, parseSoundSource(source)));
    }

    public static void stopsoundSourceSound(ServerPlayer player, String source, String sound) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(cachedId(sound), parseSoundSource(source)));
    }

    // -------------------------------------------------------------------------
    // Particle commands — native compilation helpers
    // -------------------------------------------------------------------------

    private static final java.util.concurrent.ConcurrentHashMap<String, net.minecraft.core.particles.ParticleOptions> PARTICLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static net.minecraft.core.particles.@Nullable ParticleOptions parseParticle(MinecraftServer server, String particleName) {
        var cached = PARTICLE_CACHE.get(particleName);
        if (cached != null) return cached;
        try {
            var reader = new com.mojang.brigadier.StringReader(particleName);
            var result = net.minecraft.commands.arguments.ParticleArgument.readParticle(reader, server.registryAccess());
            PARTICLE_CACHE.put(particleName, result);
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse particle '{}': {}", particleName, e.getMessage());
            return null;
        }
    }

    public static void spawnParticleSimple(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos) {
        var particle = parseParticle(server, particleName);
        if (particle == null) return;
        level.sendParticles(particle, pos.x, pos.y, pos.z, 0, 0, 0, 0, 0);
    }

    // -------------------------------------------------------------------------
    // Team commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void teamAdd(MinecraftServer server, String teamName) {
        server.getScoreboard().addPlayerTeam(teamName);
    }

    public static void teamAddDisplay(MinecraftServer server, String teamName, Component displayName) {
        var team = server.getScoreboard().addPlayerTeam(teamName);
        team.setDisplayName(displayName);
    }

    public static void teamRemove(MinecraftServer server, String teamName) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team != null) server.getScoreboard().removePlayerTeam(team);
    }

    public static void teamJoin(MinecraftServer server, String teamName, Entity executor) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team != null) server.getScoreboard().addPlayerToTeam(executor.getScoreboardName(), team);
    }

    public static void teamJoinMembers(MinecraftServer server, String teamName, String member) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team != null) server.getScoreboard().addPlayerToTeam(member, team);
    }

    public static void teamLeave(MinecraftServer server, String member) {
        server.getScoreboard().removePlayerFromTeam(member);
    }

    public static void teamEmpty(MinecraftServer server, String teamName) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team != null) {
            for (var member : new java.util.ArrayList<>(team.getPlayers())) {
                server.getScoreboard().removePlayerFromTeam(member, team);
            }
        }
    }

    public static void teamModify(MinecraftServer server, String teamName, String option, String value) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) return;
        switch (option) {
            case "color" -> {
                var color = net.minecraft.ChatFormatting.getByName(value);
                if (color != null) team.setColor(color);
            }
            case "friendlyFire" -> team.setAllowFriendlyFire(Boolean.parseBoolean(value));
            case "seeFriendlyInvisibles" -> team.setSeeFriendlyInvisibles(Boolean.parseBoolean(value));
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Recipe commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void recipeGive(MinecraftServer server, ServerPlayer player, String recipeId) {
        var key = ResourceKey.create(Registries.RECIPE, cachedId(recipeId));
        server.getRecipeManager().byKey(key).ifPresent(recipe -> player.awardRecipes(java.util.List.of(recipe)));
    }

    public static void recipeTake(MinecraftServer server, ServerPlayer player, String recipeId) {
        var key = ResourceKey.create(Registries.RECIPE, cachedId(recipeId));
        server.getRecipeManager().byKey(key).ifPresent(recipe -> player.resetRecipes(java.util.List.of(recipe)));
    }

    public static void recipeGiveAll(MinecraftServer server, ServerPlayer player) {
        player.awardRecipes(server.getRecipeManager().getRecipes());
    }

    public static void recipeTakeAll(MinecraftServer server, ServerPlayer player) {
        player.resetRecipes(server.getRecipeManager().getRecipes());
    }

    // Random commands — kept as vanilla fallback (complex random sequence API)

    public static void spawnParticle(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos, Vec3 delta, double speed, int count) {
        var particle = parseParticle(server, particleName);
        if (particle == null) return;
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed);
    }

    public static void spawnParticleForPlayers(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos, Vec3 delta, double speed, int count, List<? extends Entity> viewers) {
        var particle = parseParticle(server, particleName);
        if (particle == null) return;
        for (var entity : viewers) {
            if (entity instanceof ServerPlayer player) {
                level.sendParticles(player, particle, true, true, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed);
            }
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
        return switch(tag) {
            case ByteTag cTag -> Byte.toString(cTag.byteValue());
            case ShortTag cTag -> Short.toString(cTag.shortValue());
            case IntTag cTag -> Integer.toString(cTag.intValue());
            case LongTag cTag -> Long.toString(cTag.longValue());
            case FloatTag cTag -> TAG_DECIMAL_FORMAT.format(cTag.floatValue());
            case DoubleTag cTag -> TAG_DECIMAL_FORMAT.format(cTag.doubleValue());
            case StringTag cTag -> cTag.asString().orElseThrow();
            default -> {
                var builder = new StringBuilder();
                appendWithUnitsTag(builder, tag);
                yield builder.toString();
            }
        };
    }

    private static void appendWithUnitsTag(StringBuilder builder, Tag tag) {
        switch(tag) {
            case ByteTag cTag -> {
                builder.append(cTag.byteValue());
                builder.append('b');
            }
            case ShortTag cTag -> {
                builder.append(cTag.shortValue());
                builder.append('s');
            }
            case IntTag cTag -> builder.append(cTag.intValue());
            case LongTag cTag -> {
                builder.append(cTag.longValue());
                builder.append('L');
            }
            case FloatTag cTag -> {
                builder.append(cTag.floatValue());
                builder.append('f');
            }
            case DoubleTag cTag -> {
                builder.append(cTag.doubleValue());
                builder.append('d');
            }
            case StringTag cTag -> builder.append(StringTag.quoteAndEscape(cTag.asString().orElseThrow()));
            case ByteArrayTag cTag -> {
                builder.append("[B;");
                var arr = cTag.getAsByteArray();
                for(int i = 0; i < arr.length; i++) {
                    if(i != 0) builder.append(',');
                    builder.append(arr[i]).append('B');
                }
                builder.append(']');
            }
            case IntArrayTag cTag -> {
                builder.append("[I;");
                var arr = cTag.getAsIntArray();
                for(int i = 0; i < arr.length; i++) {
                    if(i != 0) builder.append(',');
                    builder.append(arr[i]);
                }
                builder.append(']');
            }
            case LongArrayTag cTag -> {
                builder.append("[L;");
                var arr = cTag.getAsLongArray();
                for(int i = 0; i < arr.length; i++) {
                    if(i != 0) builder.append(',');
                    builder.append(arr[i]).append('L');
                }
                builder.append(']');
            }
            case ListTag cTag -> {
                builder.append("[");
                for(int i = 0; i < cTag.size(); i++) {
                    if(i != 0) builder.append(',');
                    appendWithUnitsTag(builder, cTag.get(i));
                }
                builder.append(']');
            }
            case CompoundTag cTag -> {
                builder.append('{');
                var list = new ArrayList<>(cTag.keySet());
                Collections.sort(list);

                for(int i = 0; i < list.size(); i++) {
                    if(i != 0) builder.append(',');

                    appendOptionalStringEscape(builder, list.get(i));
                    builder.append(':');
                    appendWithUnitsTag(builder, Objects.requireNonNull(cTag.get(list.get(i))));
                }

                builder.append('}');
            }
            default -> {
                var visitor = new StringTagVisitor();
                tag.accept(visitor);
                builder.append(visitor.build());
            }
        }
    }

    private static void appendOptionalStringEscape(StringBuilder builder, String string) {
        if(STRING_SIMPLE_VALUE.matcher(string).matches()) {
            builder.append(string);
        }else {
            builder.append(StringTag.quoteAndEscape(string));
        }
    }

    private static <T extends Enum<T> & net.minecraft.util.StringRepresentable> @Nullable T findBySerializedName(T[] values, String name) {
        for (T val : values) {
            if (val.getSerializedName().equals(name)) return val;
        }
        return null;
    }

    private static final Pattern STRING_SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final DecimalFormat TAG_DECIMAL_FORMAT = Util.make(new DecimalFormat("#"), format -> {
        format.setMaximumFractionDigits(15);
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });


    // -------------------------------------------------------------------------
    // getAllEntities optimization — per-tick cache + direct section iteration
    //
    // Vanilla ServerLevel.getAllEntities() creates a lazy Iterable that walks
    // PersistentEntitySectionManager → LevelEntityGetter → all EntitySections.
    // Every call re-walks the full section graph.
    //
    // Datapacks using @e compile to GET_ENTITIES which invokes getAllEntities()
    // potentially many times per tick across multiple functions. The per-tick
    // cache ensures the section graph is walked at most once per world per tick.
    //
    // Direct section storage access via entityManager.sectionStorage bypasses
    // the LevelEntityGetter interface chain. For worlds with many loaded
    // entity sections (>PARALLEL_SECTION_THRESHOLD), collection is split
    // across a dedicated ForkJoinPool so multiple cores contribute to the
    // snapshot — safe because the server thread is blocked on Future.get()
    // during that window, so no entity state mutations can race.
    // -------------------------------------------------------------------------

    private static final java.util.IdentityHashMap<ServerLevel, List<Entity>> allEntitiesCache = new java.util.IdentityHashMap<>();
    private static int allEntitiesCacheTick = Integer.MIN_VALUE;
    private static int lastEntityCountHint = 256;

    private static final int PARALLEL_SECTION_THRESHOLD = 256;
    private static final java.util.concurrent.ForkJoinPool ENTITY_COLLECT_POOL =
            new java.util.concurrent.ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

    public static java.util.Iterator<Entity> getAllEntitiesFast(ServerLevel level) {
        int currentTick = level.getServer().getTickCount();
        if (currentTick != allEntitiesCacheTick) {
            allEntitiesCache.clear();
            allEntitiesCacheTick = currentTick;
        }

        List<Entity> cached = allEntitiesCache.get(level);
        if (cached != null) return cached.iterator();

        var sectionStorage = level.entityManager.sectionStorage;
        long[] ids = sectionStorage.sectionIds.toLongArray();

        List<Entity> result;
        if (ids.length > PARALLEL_SECTION_THRESHOLD) {
            int parallelism = ENTITY_COLLECT_POOL.getParallelism();
            int chunkSize = Math.max(1, (ids.length + parallelism - 1) / parallelism);

            var futures = new ArrayList<java.util.concurrent.Future<List<Entity>>>(parallelism);
            for (int i = 0; i < ids.length; i += chunkSize) {
                final int start = i;
                final int end = Math.min(i + chunkSize, ids.length);
                futures.add(ENTITY_COLLECT_POOL.submit(() -> {
                    var partial = new ArrayList<Entity>();
                    for (int j = start; j < end; j++) {
                        var section = sectionStorage.getSection(ids[j]);
                        if (section == null || section.isEmpty() || !section.getStatus().isAccessible()) continue;
                        partial.addAll(section.storage.find(Entity.class));
                    }
                    return partial;
                }));
            }

            result = new ArrayList<>(lastEntityCountHint);
            for (var future : futures) {
                try {
                    result.addAll(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (java.util.concurrent.ExecutionException e) {
                    LOGGER.warn("Parallel entity collection failed", e.getCause());
                }
            }
        } else {
            result = new ArrayList<>(lastEntityCountHint);
            for (long id : ids) {
                var section = sectionStorage.getSection(id);
                if (section == null || section.isEmpty() || !section.getStatus().isAccessible()) continue;
                result.addAll(section.storage.find(Entity.class));
            }
        }

        lastEntityCountHint = Math.max(result.size(), 64);
        allEntitiesCache.put(level, result);
        return result.iterator();
    }

    public static List<Entity> getEntitiesInside(ServerLevel level, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int limit) {
        // Direct 3D lookup: for every (chunkX, chunkY, chunkZ) in the bounding
        // box volume, call sectionStorage.getSection(asLong(x, y, z)) once.
        // Non-existent sections return null and cost one ConcurrentHashMap miss.
        //
        // Previous implementation used sectionIds.subSet(chunksStart, chunksEnd + 1L)
        // inside a per-chunkX loop, which on the Async mod's
        // ConcurrentLongSortedSet allocates a fresh backing set and addAll-copies
        // the subrange every call — profiler showed ~0.1ms of that per
        // getEntitiesInside on a 50-player server. Direct lookup is
        // allocation-free; for a typical BB of 5x5x5 chunks it's 125 ConcurrentHashMap.get
        // calls each ≈50ns, well under the amortized subSet cost.
        var result = new ArrayList<Entity>();
        var sectionStorage = level.entityManager.sectionStorage;

        int chunkMinX = Mth.floor(minX - 2) >> 4;
        int chunkMinY = Mth.floor(minY - 4) >> 4;
        int chunkMinZ = Mth.floor(minZ - 2) >> 4;
        int chunkMaxX = Mth.floor(maxX + 2) >> 4;
        int chunkMaxY = Mth.floor(maxY) >> 4;
        int chunkMaxZ = Mth.floor(maxZ + 2) >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cy = chunkMinY; cy <= chunkMaxY; cy++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    var section = sectionStorage.getSection(SectionPos.asLong(cx, cy, cz));
                    if (section == null || section.isEmpty() || !section.getStatus().isAccessible()) continue;
                    var entities = section.storage.find(Entity.class);
                    if (entities.isEmpty()) continue;
                    for (var entity : entities) {
                        if (entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                            result.add(entity);
                            if (result.size() >= limit) return result;
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
            return entities.getFirst().getDisplayName().copy();
        }else {
            MutableComponent result = Component.empty();
            boolean first = true;

            for(var entity : entities) {
                if(!first) result.append(separator);

                result.append(entity.getDisplayName());
                first = false;
            }

            return result;
        }
    }

    // Advancement commands

    /**
     * Cross-call cache of parsed Identifiers. {@link Identifier#parse(String)} walks
     * every character validating the namespace/path grammar and allocates a new
     * Identifier instance per call. Datapacks reference a bounded set of string
     * identifiers (advancement ids, predicate ids, registry keys) so an unbounded
     * map is fine — caps out at whatever the datapack referenced.

     * Used by the advancement helpers, predicate helpers, and anywhere else a
     * hot path would otherwise re-parse a constant string id per tick.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Identifier> IDENTIFIER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Identifier cachedId(String id) {
        var cached = IDENTIFIER_CACHE.get(id);
        if (cached != null) return cached;
        var parsed = Identifier.parse(id);
        IDENTIFIER_CACHE.put(id, parsed);
        return parsed;
    }

    public static int grantAdvancementOnly(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        int count = 0;
        for (String criterion : holder.value().criteria().keySet()) {
            if (player.getAdvancements().award(holder, criterion)) count++;
        }
        return count;
    }

    public static int grantAdvancementCriterion(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        return player.getAdvancements().award(holder, criterion) ? 1 : 0;
    }

    public static int revokeAdvancementOnly(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        int count = 0;
        for (String criterion : holder.value().criteria().keySet()) {
            if (player.getAdvancements().revoke(holder, criterion)) count++;
        }
        return count;
    }

    public static int revokeAdvancementCriterion(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        return player.getAdvancements().revoke(holder, criterion) ? 1 : 0;
    }

    public static int grantAdvancementEverything(MinecraftServer server, ServerPlayer player) {
        int count = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            for (String criterion : holder.value().criteria().keySet()) {
                if (player.getAdvancements().award(holder, criterion)) count++;
            }
        }
        return count;
    }

    public static int revokeAdvancementEverything(MinecraftServer server, ServerPlayer player) {
        int count = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            for (String criterion : holder.value().criteria().keySet()) {
                if (player.getAdvancements().revoke(holder, criterion)) count++;
            }
        }
        return count;
    }

    public static int grantAdvancementTree(MinecraftServer server, ServerPlayer player, String id, String mode) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        AdvancementNode node = server.getAdvancements().tree().get(holder);
        if (node == null) return 0;
        int count = 0;
        for (AdvancementHolder adv : collectTreeAdvancements(node, mode)) {
            for (String criterion : adv.value().criteria().keySet()) {
                if (player.getAdvancements().award(adv, criterion)) count++;
            }
        }
        return count;
    }

    public static int revokeAdvancementTree(MinecraftServer server, ServerPlayer player, String id, String mode) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        AdvancementNode node = server.getAdvancements().tree().get(holder);
        if (node == null) return 0;
        int count = 0;
        for (AdvancementHolder adv : collectTreeAdvancements(node, mode)) {
            for (String criterion : adv.value().criteria().keySet()) {
                if (player.getAdvancements().revoke(adv, criterion)) count++;
            }
        }
        return count;
    }

    private static List<AdvancementHolder> collectTreeAdvancements(AdvancementNode node, String mode) {
        List<AdvancementHolder> result = new ArrayList<>();
        switch (mode) {
            case "from" -> {
                result.add(node.holder());
                collectChildren(node, result);
            }
            case "until" -> {
                collectParents(node, result);
                result.add(node.holder());
            }
            case "through" -> {
                collectParents(node, result);
                result.add(node.holder());
                collectChildren(node, result);
            }
        }
        return result;
    }

    private static void collectChildren(AdvancementNode node, List<AdvancementHolder> result) {
        for (AdvancementNode child : node.children()) {
            result.add(child.holder());
            collectChildren(child, result);
        }
    }

    private static void collectParents(AdvancementNode node, List<AdvancementHolder> result) {
        AdvancementNode parent = node.parent();
        if (parent != null) {
            collectParents(parent, result);
            result.add(parent.holder());
        }
    }

    // Selector advancement checks

    public static boolean isAdvancementDone(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static boolean isAdvancementCriterionDone(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return false;
        CriterionProgress progress = player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion);
        return progress != null && progress.isDone();
    }

    // Selector predicate check (used by @e[predicate=...] selector filters)

    public static boolean testEntityPredicate(MinecraftServer server, Entity entity, String predicateId) {
        var lookupOpt = server.reloadableRegistries().lookup().lookup(Registries.PREDICATE);
        if (lookupOpt.isEmpty()) return false;
        var holderOpt = lookupOpt.orElseThrow().get(ResourceKey.create(Registries.PREDICATE, cachedId(predicateId)));
        if (holderOpt.isEmpty()) return false;
        LootItemCondition condition = holderOpt.orElseThrow().value();
        if (!(entity.level() instanceof ServerLevel level)) return false;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        return condition.test(ctx);
    }

    // Execute if predicate check (uses COMMAND param set with position context, entity optional)

    public static boolean testCommandPredicate(MinecraftServer server, @Nullable Entity entity, ServerLevel level, Vec3 position, String predicateId) {
        var lookupOpt = server.reloadableRegistries().lookup().lookup(Registries.PREDICATE);
        if (lookupOpt.isEmpty()) return false;
        var holderOpt = lookupOpt.orElseThrow().get(ResourceKey.create(Registries.PREDICATE, cachedId(predicateId)));
        if (holderOpt.isEmpty()) return false;
        var holder = holderOpt.orElseThrow();
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, position);
        if (entity != null) {
            builder.withOptionalParameter(LootContextParams.THIS_ENTITY, entity);
        }
        LootParams params = builder.create(LootContextParamSets.COMMAND);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        ctx.pushVisitedElement(LootContext.createVisitedEntry(holder.value()));
        return holder.value().test(ctx);
    }

    // Selector NBT check

    // -------------------------------------------------------------------------
    // Entity NBT matching — fast-path + per-tick cache
    //
    // Entity.saveWithoutId() is very expensive for players (full DFU pipeline:
    // RecipeBook, inventory, advancements...). Two-tier strategy:
    //   1. Fast-path: resolve common NBT keys directly via entity API —
    //      zero serialization cost, covers the vast majority of datapack patterns.
    //   2. Cache: if full serialization IS needed, cache the result so the same
    //      entity is only serialized once per tick even across multiple functions.
    //
    // precomputeEntityNbtAsync() can be called by the async mod (or a pre-tick
    // mixin) to warm the cache in parallel threads while entity state is stable.
    // -------------------------------------------------------------------------

    private static final java.util.IdentityHashMap<Entity, CompoundTag> entityNbtTickCache = new java.util.IdentityHashMap<>();
    private static int entityNbtCacheTick = Integer.MIN_VALUE;

    /** Full serialization, always accurate but expensive for players. */
    private static CompoundTag computeEntityNbt(Entity entity) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            return output.buildResult();
        }
    }

    /** Returns cached NBT, computing it on first access within the current tick. */
    private static CompoundTag getCachedEntityNbt(Entity entity) {
        var level = entity.level();
        var server = level instanceof ServerLevel sl ? sl.getServer() : null;
        int currentTick = server != null ? server.getTickCount() : 0;
        if (currentTick != entityNbtCacheTick) {
            entityNbtTickCache.clear();
            entityNbtCacheTick = currentTick;
        }
        return entityNbtTickCache.computeIfAbsent(entity, ExtensionFunctions::computeEntityNbt);
    }

    /**
     * Try to evaluate an NBT pattern against an entity WITHOUT serialization.
     * Returns {@code null} if the pattern contains keys not in the fast-path set
     * (caller should fall back to full serialization).

     * Supported keys: Tags, CustomName, CustomNameVisible, Health, MaxHealth,
     * Air, MaxAir, Fire, FallDistance, OnGround, NoGravity, Invulnerable,
     * Silent, Glowing.
     */
    private static @Nullable Boolean tryMatchEntityNbtFast(Entity entity, CompoundTag pattern) {
        for (var entry : pattern.entrySet()) {
            Boolean keyResult = matchSingleKey(entity, entry.getKey(), entry.getValue());
            if (keyResult == null) return null;   // unknown key — need full serialization
            if (!keyResult) return Boolean.FALSE;  // known key, value didn't match
        }
        return Boolean.TRUE;
    }

    private static @Nullable Boolean matchSingleKey(Entity entity, String key, Tag value) {
        return switch (key) {
            case "Tags" -> {
                if (!(value instanceof ListTag tagList)) yield null;
                var entityTags = entity.entityTags();
                for (Tag t : tagList) {
                    if (!(t instanceof StringTag st)) yield null;
                    if (!entityTags.contains(st.asString().orElse(""))) yield Boolean.FALSE;
                }
                yield Boolean.TRUE;
            }
            // Component.Serializer.toJson removed in MC 26.1 — fall back to full serialization
            case "CustomName" -> null;
            case "CustomNameVisible" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.isCustomNameVisible() == (expected.byteValue() != 0);
            }
            case "Health" -> {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) yield Boolean.FALSE;
                if (!(value instanceof FloatTag expected)) yield null;
                yield living.getHealth() == expected.floatValue();
            }
            case "MaxHealth" -> {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) yield Boolean.FALSE;
                if (!(value instanceof FloatTag expected)) yield null;
                yield living.getMaxHealth() == expected.floatValue();
            }
            case "Air" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield entity.getAirSupply() == expected.shortValue();
            }
            case "MaxAir" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield entity.getMaxAirSupply() == expected.shortValue();
            }
            case "Fire" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield entity.getRemainingFireTicks() == expected.shortValue();
            }
            case "FallDistance" -> {
                if (!(value instanceof FloatTag expected)) yield null;
                yield (float) entity.fallDistance == expected.floatValue();
            }
            case "OnGround" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.onGround() == (expected.byteValue() != 0);
            }
            case "NoGravity" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.isNoGravity() == (expected.byteValue() != 0);
            }
            case "Invulnerable" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.isInvulnerable() == (expected.byteValue() != 0);
            }
            case "Silent" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.isSilent() == (expected.byteValue() != 0);
            }
            case "Glowing" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield entity.hasGlowingTag() == (expected.byteValue() != 0);
            }
            default -> null; // complex key — fall back to full serialization
        };
    }

    public static CompoundTag getEntityNbt(Entity entity) {
        return getCachedEntityNbt(entity);
    }

    public static boolean matchesEntityNbt(Entity entity, CompoundTag pattern) {
        // Fast-path: avoid saveWithoutId() for patterns using only known keys
        Boolean fast = tryMatchEntityNbtFast(entity, pattern);
        if (fast != null) return fast;
        // Fall back to full serialization (cached per tick)
        return NbtUtils.compareNbt(pattern, getCachedEntityNbt(entity), true);
    }

    /**
     * Pre-warm the entity NBT cache for a collection of entities in parallel.
     * Call this from the async mod (or a pre-tick mixin) when entity state is
     * stable (between ticks) to amortize serialization cost across CPU cores.
     * NOT safe to call while entity state may be mutated concurrently.
     */
    public static void precomputeEntityNbtAsync(java.util.List<? extends Entity> entities, MinecraftServer server) {
        int currentTick = server.getTickCount();
        if (currentTick != entityNbtCacheTick) {
            entityNbtTickCache.clear();
            entityNbtCacheTick = currentTick;
        }
        var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>(entities.size());
        // Temp per-thread results to avoid ConcurrentModificationException on IdentityHashMap
        var tempResults = new java.util.concurrent.ConcurrentHashMap<Entity, CompoundTag>(entities.size());
        for (var entity : entities) {
            if (!entityNbtTickCache.containsKey(entity)) {
                futures.add(java.util.concurrent.CompletableFuture.runAsync(
                    () -> tempResults.put(entity, computeEntityNbt(entity))
                ));
            }
        }
        if (!futures.isEmpty()) {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture<?>[0])).join();
            entityNbtTickCache.putAll(tempResults);
        }
    }



    // Text building

    public static MutableComponent createTranslatableComponent(String key, List<Component> args) {
        return Component.translatable(key, args.toArray());
    }

    // Apply a runtime-built hover-text component to a mutable Component's style.
    // Used when the hoverEvent's Text is non-constant (contains ScoreText/NbtText/Selector).
    public static void applyHoverText(MutableComponent text, Component hoverText) {
        text.setStyle(text.getStyle().withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hoverText)));
    }

    public static Component getScoreText(MinecraftServer server, List<Entity> entities, String objective) {
        if (entities.isEmpty()) return Component.empty();
        Entity entity = entities.getFirst();
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective obj = scoreboard.getObjective(objective);
        if (obj == null) return Component.empty();
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(entity, obj);
        if (info == null) return Component.empty();
        return info.formatValue(obj.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.NO_STYLE));
    }

    // Give

    /**
     * Cache of parsed item strings. Datapacks reference a bounded set of items
     * (hundreds at most for even the largest packs), so an unbounded ConcurrentHashMap
     * is fine — we pay the ItemParser cost exactly once per unique string, for
     * the lifetime of the server. Each subsequent `give @a X` with the same string
     * is a single map lookup plus inventory.add() per player.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, net.minecraft.commands.arguments.item.ItemInput> ITEM_PARSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Parse an item string using vanilla's ItemParser — supports the full
     * `id[component=value,...]` syntax with enchantments, custom names, lore,
     * attribute modifiers, and anything else the game command dispatcher
     * accepts. Cached so repeated `give` calls with the same string don't
     * reparse.

     * The input is expected to be just the item descriptor — count is now a
     * separate argument passed explicitly to giveItemsToAll, so no trailing
     * space/number splitting is needed here.

     * Returns null on malformed input (logged once per string at WARN).
     */
    private static net.minecraft.commands.arguments.item.@Nullable ItemInput parseItemInput(MinecraftServer server, String itemString) {
        var cached = ITEM_PARSE_CACHE.get(itemString);
        if (cached != null) return cached;

        var parser = new net.minecraft.commands.arguments.item.ItemParser(server.registryAccess());
        try {
            var input = parser.parse(new com.mojang.brigadier.StringReader(itemString));
            ITEM_PARSE_CACHE.put(itemString, input);
            return input;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            LOGGER.warn("Failed to parse item string '{}': {}", itemString, e.getMessage());
            return null;
        }
    }

    /**
     * Give an item stack to a list of entities (players only). All parsing
     * (vanilla ItemParser including components, enchantments, NBT) happens
     * once and is cached across calls — per-player cost is only
     * ItemStack.copyWithCount() + inventory.add().
     */
    public static void giveItemsToAll(MinecraftServer server, List<? extends Entity> entities, String itemString, int count) {
        if (entities.isEmpty() || count <= 0) return;

        var input = parseItemInput(server, itemString);
        if (input == null) return;

        // Build a prototype stack once — createItemStack validates the
        // components and max count; subsequent per-player stacks are cheap copies.
        net.minecraft.world.item.ItemStack prototype;
        try {
            prototype = input.createItemStack(1);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            LOGGER.warn("Failed to build item stack for '{}': {}", itemString, e.getMessage());
            return;
        }
        int maxStackSize = prototype.getMaxStackSize();

        for (var entity : entities) {
            if (!(entity instanceof ServerPlayer player)) continue;
            int remaining = count;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, maxStackSize);
                var stack = prototype.copyWithCount(stackSize);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= stackSize;
            }
        }
    }

    // Playsound

    /** Cached sound-event holder + source-enum pair per (sound-id, source-name) input. */
    private record SoundParsed(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder, net.minecraft.sounds.SoundSource source) {}

    /**
     * Cross-call cache of parsed (sound, source) pairs. Same sizing logic as
     * ITEM_PARSE_CACHE — datapacks use a bounded set of sounds, unbounded map
     * is fine. Key is `sound + '\0' + source` because either part alone can
     * repeat across different pairs.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, SoundParsed> SOUND_PARSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Play a sound to a list of entities (players only). Sound identifier and
     * source-category string are parsed at most once per unique pair for the
     * lifetime of the server — subsequent calls hit a ConcurrentHashMap and
     * go straight to per-player distance checks and packet dispatch.

     * Previously every call paid for Identifier.parse + SoundEvent.
     * createVariableRangeEvent + Holder.direct + String.toUpperCase +
     * SoundSource.valueOf, even when the same playsound ran thousands of
     * times from a tick function.
     */
    // Vanilla command parser accepts sound-source names like "player"/"block" (via
    // SoundSource.getName()), not the enum constant names ("PLAYERS"/"BLOCKS").
    // Match both forms so datapack strings stay valid at runtime.
    private static net.minecraft.sounds.SoundSource parseSoundSource(String name) {
        for (var s : net.minecraft.sounds.SoundSource.values()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return net.minecraft.sounds.SoundSource.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }

    public static void playSoundToAll(List<? extends Entity> entities, String sound, String source, net.minecraft.world.phys.Vec3 pos, double volume, double pitch, double minVolume) {
        if (entities.isEmpty()) return;

        String cacheKey = sound + '\0' + source;
        var parsed = SOUND_PARSE_CACHE.get(cacheKey);
        if (parsed == null) {
            var soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(sound));
            var holder = net.minecraft.core.Holder.direct(soundEvent);
            var soundSource = parseSoundSource(source);
            parsed = new SoundParsed(holder, soundSource);
            SOUND_PARSE_CACHE.put(cacheKey, parsed);
        }
        var soundHolder = parsed.holder();
        var soundSource = parsed.source();

        float dist = Math.max((float) volume, 1.0f) * 16.0f;
        float distSqThreshold = dist * dist;
        float vol = (float) volume;
        float pit = (float) pitch;
        float minVol = (float) minVolume;

        for (var entity : entities) {
            if (!(entity instanceof ServerPlayer player)) continue;
            double dx = pos.x - player.getX();
            double dy = pos.y - player.getY();
            double dz = pos.z - player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            long seed = player.level().getRandom().nextLong();
            if (distSq > distSqThreshold) {
                if (minVol <= 0.0f) continue;
                double d = Math.sqrt(distSq);
                double nx = player.getX() + dx / d * 2.0;
                double ny = player.getY() + dy / d * 2.0;
                double nz = player.getZ() + dz / d * 2.0;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    soundHolder, soundSource, nx, ny, nz, minVol, pit, seed
                ));
            } else {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    soundHolder, soundSource, pos.x, pos.y, pos.z, vol, pit, seed
                ));
            }
        }
    }

    // Summon

    public static void summonEntity(MinecraftServer server, ServerLevel world, String entityType, net.minecraft.world.phys.Vec3 pos) {
        summonEntityNbt(server, world, entityType, pos, new CompoundTag());
    }

    public static void summonEntityNbt(MinecraftServer server, ServerLevel world, String entityType, Vec3 pos, CompoundTag nbt) {
        nbt.putString("id", entityType);
        Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt, world, net.minecraft.world.entity.EntitySpawnReason.COMMAND, e -> {
            e.snapTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
            return e;
        });
        if (entity != null) {
            // TODO: Add finalize boolean check
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
            }
            if (world.tryAddFreshEntityWithPassengers(entity)) {
                // Invalidate the per-tick entity cache so subsequent @e sees the new entity
                allEntitiesCache.remove(world);
            }
        }
    }

    // Time

    public static void setTime(MinecraftServer server, ServerLevel world, int time) {
        ServerClockManager clockManager = server.clockManager();
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        clockManager.setTotalTicks(clock, time);
    }

    public static void setTimeToMarker(MinecraftServer server, ServerLevel world, String timeMarkerId) {
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        server.clockManager().moveToTimeMarker(clock, ResourceKey.create(ClockTimeMarkers.ROOT_ID, Identifier.parse(timeMarkerId)));
    }

    public static void addTime(MinecraftServer server, ServerLevel world, int time) {
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        server.clockManager().addTicks(clock, time);
    }

    public static void setTimeRate(MinecraftServer server, ServerLevel world, float rate) {
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        server.clockManager().setRate(clock, rate);
    }

    public static void setTimePaused(MinecraftServer server, ServerLevel world, boolean paused) {
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        server.clockManager().setPaused(clock, paused);
    }

    public static int queryTime(MinecraftServer server, ServerLevel world) {
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();

        long totalTicks = server.clockManager().getTotalTicks(clock);
        return TimeCommand.wrapTime(totalTicks);
    }

    public static int queryTimelineTicks(MinecraftServer server, ServerLevel world, String timelineId) {
        ServerClockManager clockManager = server.clockManager();
        var timelineOpt = world.registryAccess().get(
                ResourceKey.create(Registries.TIMELINE, Identifier.parse(timelineId))
        );
        if (timelineOpt.isEmpty()) return 0;
        long currentTicks = timelineOpt.orElseThrow().value().getCurrentTicks(clockManager);
        return TimeCommand.wrapTime(currentTicks);
    }

    public static int queryTimelineRepetitions(MinecraftServer server, ServerLevel world, String timelineId) {
        ServerClockManager clockManager = server.clockManager();
        var timelineOpt = world.registryAccess().get(
                ResourceKey.create(Registries.TIMELINE, Identifier.parse(timelineId))
        );
        if (timelineOpt.isEmpty()) return 0;
        long repetitions = timelineOpt.orElseThrow().value().getPeriodCount(clockManager);
        return TimeCommand.wrapTime(repetitions);
    }

    public static int queryGameTime(MinecraftServer server, ServerLevel world) {
        long gameTime = world.getGameTime();
        return TimeCommand.wrapTime(gameTime);
    }

    // Schedule

    public static void scheduleFunction(MinecraftServer server, String function, int time, boolean append) {
        var timerQueue = server.getScheduledEvents();
        long targetTime = server.overworld().getGameTime() + time;
        if (!append) {
            timerQueue.remove(function);
        }
        timerQueue.schedule(function, targetTime, new net.minecraft.world.level.timers.FunctionCallback(Identifier.parse(function)));
    }

    public static void scheduleClear(MinecraftServer server, String function) {
        server.getScheduledEvents().remove(function);
    }

    // Gamerule

    public static void setGamerule(MinecraftServer server, String rule, String value) {
        // Direct API path: parse the value via the rule's own deserialize(),
        // then call GameRules.set(rule, value, server). This is exactly what
        // GameRuleCommand.setRule() does internally, minus the chat broadcast
        // that 26.1+ added for every /gamerule invocation (even no-ops) and
        // the command-source permission check.
        //
        // Verified against 26.1 server.jar:
        //   GameRule<T>.deserialize(String) -> DataResult<T>
        //   GameRules.set(GameRule<T>, T, MinecraftServer)
        //
        // Datapacks that set gamerules on per-player reward chains (e.g. BACAP
        // running `gamerule command_block_output false` in bacap/root.mcfunction
        // on every advancement) were spamming
        // `[Server: Gamerule X is now set to: Y]` to every player on 26.1+.
        var gameRules = server.getGameRules();
        gameRules.visitGameRuleTypes(new net.minecraft.world.level.gamerules.GameRuleTypeVisitor() {
            @Override
            public <T> void visit(net.minecraft.world.level.gamerules.GameRule<T> gameRule) {
                if (!rule.equals(gameRule.id())) return;
                gameRule.deserialize(value).result().ifPresent(v -> gameRules.set(gameRule, v, server));
            }
        });
    }

    public static int queryGamerule(MinecraftServer server, String rule) {
        var gameRules = server.overworld().getGameRules();
        int[] result = {0};
        gameRules.visitGameRuleTypes(new net.minecraft.world.level.gamerules.GameRuleTypeVisitor() {
            @Override
            public <T> void visit(net.minecraft.world.level.gamerules.GameRule<T> gameRule) {
                if (rule.equals(gameRule.id())) {
                    T val = gameRules.get(gameRule);
                    result[0] = gameRule.getCommandResult(val);
                }
            }
        });
        return result[0];
    }

    // Datapack

    public static void enableDatapack(MinecraftServer server, String name) {
        var repo = server.getPackRepository();
        var pack = repo.getPack(name);
        if (pack != null && !repo.getSelectedPacks().contains(pack)) {
            var selected = new java.util.ArrayList<>(repo.getSelectedIds());
            selected.add(name);
            server.reloadResources(selected);
        }
    }

    public static void disableDatapack(MinecraftServer server, String name) {
        var repo = server.getPackRepository();
        var pack = repo.getPack(name);
        if (pack != null && repo.getSelectedPacks().contains(pack)) {
            var selected = new java.util.ArrayList<>(repo.getSelectedIds());
            selected.remove(name);
            server.reloadResources(selected);
        }
    }

    // Vanilla fallback for partial compilation

    public static void executeVanillaCommand(CommandSourceStack stack, String command) {
        stack.getServer().getCommands().performPrefixedCommand(stack, command);
        // Vanilla commands can summon/kill entities or modify entity NBT,
        // so invalidate both caches to prevent stale data in subsequent selectors
        allEntitiesCache.clear();
        entityNbtTickCache.clear();
    }

    // ScoreText с именованным score holder
    public static Component getScoreTextNamed(MinecraftServer server, String holderName, String objective) {
        var scoreboard = server.getScoreboard();
        var obj = scoreboard.getObjective(objective);
        if (obj == null) return Component.empty();
        var holder = net.minecraft.world.scores.ScoreHolder.forNameOnly(holderName);
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, obj);
        if (info == null) return Component.empty();
        return info.formatValue(obj.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.NO_STYLE));
    }

    // Поиск первого NBT элемента в ListTag по compound-фильтру
    public static @Nullable Tag findFirstNbtMatchingCompound(ListTag list, CompoundTag filter) {
        for (Tag element : list) {
            if (element instanceof CompoundTag compound && NbtUtils.compareNbt(filter, compound, true)) {
                return element;
            }
        }
        return null;
    }

    // CompoundCheck фильтр в NBT path
    public static boolean matchesNbtFilter(Tag tag, CompoundTag filter) {
        if (tag instanceof CompoundTag compound) {
            return NbtUtils.compareNbt(filter, compound, true);
        }
        return false;
    }

    // NbtText — получить NBT из storage как Component
    public static Component getNbtText(net.minecraft.world.level.storage.CommandStorage storage,
            String storageName, String path) {
        var compound = storage.get(Identifier.parse(storageName));
        return walkNbtPathAsText(compound, path);
    }

    // NbtText — получить NBT из первой подходящей entity как Component
    public static Component getEntityNbtText(List<? extends Entity> entities, String path) {
        if (entities.isEmpty()) return Component.empty();
        return walkNbtPathAsText(getEntityNbt(entities.getFirst()), path);
    }

    private static Component walkNbtPathAsText(net.minecraft.nbt.@Nullable Tag root, String path) {
        net.minecraft.nbt.Tag current = root;
        if (current == null) return Component.empty();
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) continue;
            if (current instanceof CompoundTag ct) {
                current = ct.get(part);
            } else {
                return Component.empty();
            }
            if (current == null) return Component.empty();
        }
        return Component.literal(stringifyTag(current));
    }

    // data get — конвертация NBT Tag в int с масштабом

    public static int nbtTagToInt(Tag tag, double scale) {
        if (tag instanceof net.minecraft.nbt.NumericTag numeric) {
            return (int)(numeric.doubleValue() * scale);
        }
        if (tag instanceof net.minecraft.nbt.CollectionTag collection) {
            return (int)(collection.size() * scale);
        }
        if (tag instanceof CompoundTag compound) {
            return (int)(compound.size() * scale);
        }
        if (tag instanceof net.minecraft.nbt.StringTag str) {
            return (int)(str.asString().orElse("").length() * scale);
        }
        return 0;
    }

    // Scoreboard objectives

    public static void addObjective(net.minecraft.server.ServerScoreboard scoreboard, String name, String criteriaId, net.minecraft.network.chat.@Nullable Component displayName) {
        if (scoreboard.getObjective(name) != null) return;
        var criterion = net.minecraft.world.scores.criteria.ObjectiveCriteria.byName(criteriaId)
            .orElse(net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY);
        var display = displayName != null ? displayName : net.minecraft.network.chat.Component.literal(name);
        scoreboard.addObjective(name, criterion, display,
            criterion.getDefaultRenderType(), false, null);
    }

    // NBT list operations

    public static boolean nbtListPrepend(ListTag list, Tag value) {
        return list.addTag(0, value);
    }

    public static boolean nbtListAppend(ListTag list, Tag value) {
        return list.addTag(list.size(), value);
    }

    public static boolean nbtListInsert(ListTag list, int index, Tag value) {
        if (index < 0 || index > list.size()) return false;
        return list.addTag(index, value);
    }

    // Dimension level

    public static net.minecraft.server.level.@Nullable ServerLevel getDimensionLevel(MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    // Block checking для execute if block

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState>> BLOCK_PREDICATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean checkBlock(net.minecraft.server.level.ServerLevel level, Vec3 position, String predicate) {
        var blockPos = net.minecraft.core.BlockPos.containing(position);
        var state = level.getBlockState(blockPos);
        // Fast path: cached predicates without NBT
        var cached = BLOCK_PREDICATE_CACHE.get(predicate);
        if (cached != null) return cached.test(state);
        try {
            var blockLookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            var parsed = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForTesting(blockLookup, predicate, true);
            return parsed.map(
                blockResult -> {
                    var targetState = blockResult.blockState();
                    var props = blockResult.properties();
                    var nbt = blockResult.nbt();
                    // Check block type + specified properties
                    if (!state.is(targetState.getBlock())) return false;
                    for (var prop : props.entrySet()) {
                        if (!Objects.equals(state.getValue(prop.getKey()), prop.getValue())) return false;
                    }
                    // Check NBT if specified
                    if (nbt != null) {
                        var blockEntity = level.getBlockEntity(blockPos);
                        if (blockEntity == null) return false;
                        var beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        if (!NbtUtils.compareNbt(nbt, beTag, true)) return false;
                    } else {
                        // Cache only predicates without NBT (state-only checks are position-independent)
                        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> cacheable = s -> {
                            if (!s.is(targetState.getBlock())) return false;
                            for (var p : props.entrySet()) {
                                if (!Objects.equals(s.getValue(p.getKey()), p.getValue())) return false;
                            }
                            return true;
                        };
                        BLOCK_PREDICATE_CACHE.put(predicate, cacheable);
                    }
                    return true;
                },
                tagResult -> {
                    var tag = tagResult.tag();
                    var vagueProps = tagResult.vagueProperties();
                    if (!state.is(tag)) return false;
                    for (var entry : vagueProps.entrySet()) {
                        var prop = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                        if (prop == null) return false;
                        var val = prop.getValue(entry.getValue());
                        if (val.isEmpty() || !Objects.equals(state.getValue(prop), val.get())) return false;
                    }
                    // Tags without NBT can be cached
                    if (tagResult.nbt() == null) {
                        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> cacheable = s -> {
                            if (!s.is(tag)) return false;
                            for (var e : vagueProps.entrySet()) {
                                var p = s.getBlock().getStateDefinition().getProperty(e.getKey());
                                if (p == null) return false;
                                var v = p.getValue(e.getValue());
                                if (v.isEmpty() || !Objects.equals(s.getValue(p), v.get())) return false;
                            }
                            return true;
                        };
                        BLOCK_PREDICATE_CACHE.put(predicate, cacheable);
                    } else {
                        var blockEntity = level.getBlockEntity(blockPos);
                        if (blockEntity == null) return false;
                        var beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        if (!NbtUtils.compareNbt(tagResult.nbt(), beTag, true)) return false;
                    }
                    return true;
                }
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to parse block predicate '{}': {}", predicate, e.getMessage());
            return false;
        }
    }

    // Entity relations для execute on leasher / attacker / owner

    public static @Nullable Entity getEntityLeashHolder(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            return mob.getLeashHolder();
        }
        return null;
    }

    public static @Nullable Entity getEntityLastAttacker(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getLastHurtByMob();
        }
        return null;
    }

    public static @Nullable Entity getEntityOwner(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
            return ownable.getOwner();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Scoreboard display — setdisplay, players display name/numberformat
    // -------------------------------------------------------------------------

    public static void scoreboardSetdisplayClear(MinecraftServer server, String slotName) {
        var slot = net.minecraft.world.scores.DisplaySlot.CODEC.byName(slotName);
        if (slot != null) server.getScoreboard().setDisplayObjective(slot, null);
    }

    public static void scoreboardSetdisplay(MinecraftServer server, String slotName, String objectiveName) {
        var slot = net.minecraft.world.scores.DisplaySlot.CODEC.byName(slotName);
        if (slot == null) return;
        var obj = server.getScoreboard().getObjective(objectiveName);
        if (obj != null) server.getScoreboard().setDisplayObjective(slot, obj);
    }

    public static void scoreboardPlayersDisplayName(ServerScoreboard scoreboard, ScoreHolder holder, String objectiveName, @Nullable Component displayName) {
        var obj = scoreboard.getObjective(objectiveName);
        if (obj == null) return;
        scoreboard.getOrCreatePlayerScore(holder, obj).display(displayName);
    }

    public static void scoreboardPlayersNumberformatBlank(ServerScoreboard scoreboard, ScoreHolder holder, String objectiveName) {
        var obj = scoreboard.getObjective(objectiveName);
        if (obj == null) return;
        scoreboard.getOrCreatePlayerScore(holder, obj).numberFormatOverride(net.minecraft.network.chat.numbers.BlankFormat.INSTANCE);
    }

    public static void scoreboardPlayersNumberformatStyled(MinecraftServer server, ServerScoreboard scoreboard, ScoreHolder holder, String objectiveName, String styleJson) {
        var obj = scoreboard.getObjective(objectiveName);
        if (obj == null) return;
        try {
            var style = net.minecraft.network.chat.Style.Serializer.CODEC.parse(
                server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                com.google.gson.JsonParser.parseString(styleJson)
            ).getOrThrow();
            scoreboard.getOrCreatePlayerScore(holder, obj).numberFormatOverride(new net.minecraft.network.chat.numbers.StyledFormat(style));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse style for numberformat: {}", styleJson, e);
        }
    }

    public static void scoreboardPlayersNumberformatFixed(ServerScoreboard scoreboard, ScoreHolder holder, String objectiveName, Component text) {
        var obj = scoreboard.getObjective(objectiveName);
        if (obj == null) return;
        scoreboard.getOrCreatePlayerScore(holder, obj).numberFormatOverride(new net.minecraft.network.chat.numbers.FixedFormat(text));
    }

    // -------------------------------------------------------------------------
    // Team modify — visibility, collision, prefix, suffix, displayName
    // -------------------------------------------------------------------------

    public static void teamModifyFull(MinecraftServer server, String teamName, String option, String value) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) return;
        switch (option) {
            case "color" -> {
                var color = net.minecraft.ChatFormatting.getByName(value);
                if (color != null) team.setColor(color);
            }
            case "friendlyFire" -> team.setAllowFriendlyFire(Boolean.parseBoolean(value));
            case "seeFriendlyInvisibles" -> team.setSeeFriendlyInvisibles(Boolean.parseBoolean(value));
            case "nametagVisibility" -> {
                var vis = findBySerializedName(net.minecraft.world.scores.Team.Visibility.values(), value);
                if (vis != null) team.setNameTagVisibility(vis);
            }
            case "deathMessageVisibility" -> {
                var vis = findBySerializedName(net.minecraft.world.scores.Team.Visibility.values(), value);
                if (vis != null) team.setDeathMessageVisibility(vis);
            }
            case "collisionRule" -> {
                var rule = findBySerializedName(net.minecraft.world.scores.Team.CollisionRule.values(), value);
                if (rule != null) team.setCollisionRule(rule);
            }
            case "prefix" -> {
                try {
                    var component = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(
                        server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                        com.google.gson.JsonParser.parseString(value)
                    ).getOrThrow();
                    team.setPlayerPrefix(component);
                } catch (Exception ignored) {}
            }
            case "suffix" -> {
                try {
                    var component = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(
                        server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                        com.google.gson.JsonParser.parseString(value)
                    ).getOrThrow();
                    team.setPlayerSuffix(component);
                } catch (Exception ignored) {}
            }
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Random commands — value, roll, reset
    // -------------------------------------------------------------------------

    public static int randomValue(ServerLevel level, int min, int max) {
        return Mth.randomBetweenInclusive(level.getRandom(), min, max);
    }

    public static int randomValueSequence(MinecraftServer server, int min, int max, String sequenceId) {
        var source = server.getRandomSequence(cachedId(sequenceId));
        return Mth.randomBetweenInclusive(source, min, max);
    }

    public static void randomResetSequence(MinecraftServer server, ServerLevel level, String sequenceId) {
        server.getRandomSequences().reset(cachedId(sequenceId), level.getSeed());
    }

    public static void randomResetAll(MinecraftServer server) {
        server.getRandomSequences().clear();
    }

    // -------------------------------------------------------------------------
    // Setblock / Fill — native block placement
    // -------------------------------------------------------------------------

    private static final java.util.concurrent.ConcurrentHashMap<String, net.minecraft.commands.arguments.blocks.BlockInput> BLOCK_INPUT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static net.minecraft.commands.arguments.blocks.@Nullable BlockInput parseBlockInput(MinecraftServer server, String blockString) {
        var cached = BLOCK_INPUT_CACHE.get(blockString);
        if (cached != null) return cached;
        try {
            var result = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                server.registryAccess().lookupOrThrow(Registries.BLOCK), blockString, true);
            var input = new net.minecraft.commands.arguments.blocks.BlockInput(result.blockState(), result.properties().keySet(), result.nbt());
            BLOCK_INPUT_CACHE.put(blockString, input);
            return input;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse block state '{}': {}", blockString, e.getMessage());
            return null;
        }
    }

    public static int setBlock(MinecraftServer server, ServerLevel level, Vec3 pos, String blockString, int mode) {
        var input = parseBlockInput(server, blockString);
        if (input == null) return 0;
        var blockPos = net.minecraft.core.BlockPos.containing(pos);
        if (!level.isInWorldBounds(blockPos)) return 0;
        switch (mode) {
            case 1 -> { // keep — only place if current block is air
                if (!level.getBlockState(blockPos).isAir()) return 0;
            }
            case 2 -> // destroy — break the block first
                level.destroyBlock(blockPos, true);
            default -> {} // replace — no pre-processing
        }
        if (!input.place(level, blockPos, 2 | 256)) return 0;
        level.updateNeighboursOnBlockSet(blockPos, level.getBlockState(blockPos));
        return 1;
    }

    public static int fillBlocks(MinecraftServer server, ServerLevel level, Vec3 from, Vec3 to, String blockString, int mode, @Nullable String filterString) {
        var input = parseBlockInput(server, blockString);
        if (input == null) return 0;
        java.util.function.@Nullable Predicate<net.minecraft.world.level.block.state.BlockState> filterPredicate = null;
        if (filterString != null) {
            var cached = BLOCK_PREDICATE_CACHE.get(filterString);
            if (cached != null) {
                filterPredicate = cached;
            } else {
                // trigger cache population via checkBlock
                checkBlock(level, from, filterString);
                filterPredicate = BLOCK_PREDICATE_CACHE.get(filterString);
            }
        }
        var fromPos = net.minecraft.core.BlockPos.containing(from);
        var toPos = net.minecraft.core.BlockPos.containing(to);
        var region = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(fromPos, toPos);
        int count = 0;
        var updatePositions = new java.util.ArrayList<net.minecraft.core.BlockPos>();

        for (var blockPos : net.minecraft.core.BlockPos.betweenClosed(
                region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ())) {
            // mode: 0=replace, 1=replace_filter, 2=keep, 3=destroy, 4=hollow, 5=outline
            switch (mode) {
                case 1 -> { // replace with filter — only replace blocks matching filter predicate
                    if (filterPredicate != null && !filterPredicate.test(level.getBlockState(blockPos))) continue;
                }
                case 2 -> { // keep
                    if (!level.getBlockState(blockPos).isAir()) continue;
                }
                case 3 -> { // destroy
                    level.destroyBlock(blockPos, true);
                }
                case 4 -> { // hollow
                    boolean isEdge = blockPos.getX() == region.minX() || blockPos.getX() == region.maxX()
                            || blockPos.getY() == region.minY() || blockPos.getY() == region.maxY()
                            || blockPos.getZ() == region.minZ() || blockPos.getZ() == region.maxZ();
                    if (!isEdge) {
                        // interior — set to air
                        var airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                        if (!level.getBlockState(blockPos).equals(airState)) {
                            level.setBlock(blockPos, airState, 2 | 256);
                            updatePositions.add(blockPos.immutable());
                            count++;
                        }
                        continue;
                    }
                }
                case 5 -> { // outline
                    boolean isEdge = blockPos.getX() == region.minX() || blockPos.getX() == region.maxX()
                            || blockPos.getY() == region.minY() || blockPos.getY() == region.maxY()
                            || blockPos.getZ() == region.minZ() || blockPos.getZ() == region.maxZ();
                    if (!isEdge) continue;
                }
            }
            if (input.place(level, blockPos, 2 | 256)) {
                updatePositions.add(blockPos.immutable());
                count++;
            }
        }
        for (var pos2 : updatePositions) {
            level.updateNeighboursOnBlockSet(pos2, level.getBlockState(pos2));
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Teleport facing — tp with facing entity / coordinates
    // -------------------------------------------------------------------------

    public static void teleportFacingPos(Entity target, ServerLevel world, Vec3 pos, Vec3 facingPos) {
        double dx = facingPos.x - pos.x;
        double dy = facingPos.y - pos.y;
        double dz = facingPos.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float xRot = Mth.wrapDegrees((float)(-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        float yRot = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f);
        if (target.teleportTo(world, pos.x, pos.y, pos.z, java.util.Set.of(), yRot, xRot, true)) {
            postTeleportCleanup(target);
        }
    }

    public static void teleportFacingEntity(Entity target, ServerLevel world, Vec3 pos, Entity facingEntity, boolean useEyes) {
        double ey = useEyes && facingEntity instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getY() + living.getEyeHeight()
                : facingEntity.getY();
        teleportFacingPos(target, world, pos, new Vec3(facingEntity.getX(), ey, facingEntity.getZ()));
    }

    // -------------------------------------------------------------------------
    // Data operations — merge entity, remove storage/entity, modify from/string
    // -------------------------------------------------------------------------

    public static void dataMergeEntity(Entity entity, MinecraftServer server, CompoundTag patch) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        var uuid = entity.getUUID();
        var current = net.minecraft.advancements.criterion.NbtPredicate.getEntityTagToCompare(entity);
        current.merge(patch);
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            entity.load(net.minecraft.world.level.storage.TagValueInput.create(reporter, server.registryAccess(), current));
            entity.setUUID(uuid);
        }
        entityNbtTickCache.remove(entity);
    }

    public static void dataRemoveStorage(net.minecraft.world.level.storage.CommandStorage storage, String storageName, String path) {
        var id = cachedId(storageName);
        try {
            var nbtPath = net.minecraft.commands.arguments.NbtPathArgument.NbtPath.of(path);
            var data = storage.get(id);
            nbtPath.remove(data);
            storage.set(id, data);
        } catch (Exception e) {
            LOGGER.warn("Failed to remove data from storage {}: {}", storageName, e.getMessage());
        }
    }

    public static void dataRemoveEntity(Entity entity, MinecraftServer server, String path) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        var compound = net.minecraft.advancements.criterion.NbtPredicate.getEntityTagToCompare(entity);
        try {
            var nbtPath = net.minecraft.commands.arguments.NbtPathArgument.NbtPath.of(path);
            nbtPath.remove(compound);
            var uuid = entity.getUUID();
            try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                entity.load(net.minecraft.world.level.storage.TagValueInput.create(reporter, server.registryAccess(), compound));
                entity.setUUID(uuid);
            }
            entityNbtTickCache.remove(entity);
        } catch (Exception e) {
            LOGGER.warn("Failed to remove data from entity: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Execute summon / positioned over
    // -------------------------------------------------------------------------

    public static @Nullable Entity executeSummon(CommandSourceStack source, String entityTypeId) {
        try {
            var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(cachedId(entityTypeId));
            if (entityTypeOpt.isEmpty()) return null;
            var level = source.getLevel();
            var entity = entityTypeOpt.get().value().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (entity == null) return null;
            var pos = source.getPosition();
            entity.snapTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
            if (!level.tryAddFreshEntityWithPassengers(entity)) return null;
            allEntitiesCache.clear();
            return entity;
        } catch (Exception e) {
            LOGGER.warn("Failed to summon entity '{}': {}", entityTypeId, e.getMessage());
            return null;
        }
    }

    public static Vec3 positionedOver(ServerLevel level, Vec3 pos, String heightmapType) {
        var heightmap = net.minecraft.world.level.levelgen.Heightmap.Types.valueOf(heightmapType.toUpperCase(Locale.ROOT));
        int height = level.getHeight(heightmap, Mth.floor(pos.x), Mth.floor(pos.z));
        return new Vec3(pos.x, height, pos.z);
    }

}