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
import net.minecraft.world.timeline.Timeline;
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
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import dev.epicpix.minecraftfunctioncompiler.mod.mixin.ScoreboardInvoker;
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

    // Score read/write path. Mirrors one vanilla Scoreboard.getOrCreatePlayerScore ScoreAccess per command and
    // target, but works on the live Score objects directly (no ScoreAccess / lambda / MutableBoolean allocation).
    // Constant holders go through ScoreRef (link-time cached lookup); these helpers serve every holder.

    /** The live score of {@code holder} in {@code objective}, or null (vanilla getPlayerScoreInfo; never creates). */
    public static @Nullable Score findScore(Scoreboard scoreboard, Objective objective, ScoreHolder holder) {
        PlayerScores scores = scoreboard.playerScores.get(holder.getScoreboardName());
        return scores == null ? null : scores.get(objective);
    }

    /** {@code found}, or the score vanilla getOrCreatePlayerScore creates for it (value 0, locked). The creation is
     * synced by the following {@link #setScore} call with {@code found == null}, as the creating ScoreAccess does. */
    public static Score ensureScore(ServerScoreboard scoreboard, Objective objective, ScoreHolder holder, @Nullable Score found) {
        if (found != null) return found;
        scoreboard.getOrCreatePlayerScore(holder, objective, true);
        return scoreboard.playerScores.get(holder.getScoreboardName()).get(objective);
    }

    /**
     * Vanilla ScoreAccess.set on the access that looked {@code score} up; {@code found == null} means that access
     * created it (requiresSync), so the score is synced even when the value does not change.
     * A read-only criterion is left untouched: scoreboard set/add/remove/operation reject it before reaching here
     * (getWritableObjective), and for `execute store` vanilla's set would throw IllegalStateException instead.
     */
    public static void setScore(ServerScoreboard scoreboard, Objective objective, ScoreHolder holder, Score score, @Nullable Score found, int value) {
        if (objective.getCriteria().isReadOnly()) return;
        boolean changed = found == null;
        if (objective.displayAutoUpdate()) {
            Component display = holder.getDisplayName();
            if (display != null && !display.equals(score.display())) {
                score.display(display);
                changed = true;
            }
        }
        if (value != score.value()) {
            score.value(value);
            changed = true;
        }
        if (changed) ((ScoreboardInvoker) scoreboard).mcfc$onScoreChanged(holder, objective, score);
    }

    /** One target of vanilla ScoreboardCommand.enableTrigger: unlocks a locked score; returns whether it did. */
    public static boolean enableTrigger(ServerScoreboard scoreboard, Objective objective, ScoreHolder holder) {
        ScoreAccess access = scoreboard.getOrCreatePlayerScore(holder, objective);
        if (!access.locked()) return false;
        access.unlock();
        return true;
    }

    // Tag creation helpers for execute store byte/short/long
    public static Tag createByteTag(int value) { return ByteTag.valueOf((byte) value); }
    public static Tag createShortTag(int value) { return ShortTag.valueOf((short) value); }
    public static Tag createLongTag(int value) { return LongTag.valueOf(value); }
    public static Tag createLongTagFromDouble(double value) { return LongTag.valueOf((long) value); }

    // -------------------------------------------------------------------------
    // Effect commands — native compilation helpers
    // -------------------------------------------------------------------------

    /** {@code seconds} argument value meaning "omitted" ({@code effect give <targets> <effect>}). */
    public static final int EFFECT_SECONDS_OMITTED = -1;

    /**
     * {@code effect give}: {@code seconds} is the raw command argument or
     * {@link #EFFECT_SECONDS_OMITTED}. Duration follows 26.3 EffectCommands.computeDurationInTicks:
     * omitted → 600 ticks (1 for instantaneous effects), otherwise seconds*20 (seconds as ticks
     * for instantaneous effects). Returns 1 when the effect was applied, like vanilla's per-entity count.
     */
    public static int effectGive(Entity entity, @Nullable Entity sourceEntity, MinecraftServer server, String effectId, int seconds, int amplifier, boolean hideParticles) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return 0;
        var effect = mobEffect(server, effectId);
        if (effect == null) return 0;
        boolean instantaneous = effect.value().isInstantaneous();
        int durationTicks = seconds == EFFECT_SECONDS_OMITTED
                ? (instantaneous ? 1 : 600)
                : (instantaneous ? seconds : seconds * 20);
        return living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, durationTicks, amplifier, false, !hideParticles), sourceEntity) ? 1 : 0;
    }

    public static void effectClearAll(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.removeAllEffects();
        }
    }

    public static void effectClearEffect(Entity entity, MinecraftServer server, String effectId) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return;
        var effect = mobEffect(server, effectId);
        if (effect != null) living.removeEffect(effect);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> MOB_EFFECT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Registry holder for a compile-time effect id, resolved once (vanilla ResourceArgument); null = unknown. */
    private static @Nullable Holder<net.minecraft.world.effect.MobEffect> mobEffect(MinecraftServer server, String effectId) {
        Object cached = MOB_EFFECT_CACHE.get(effectId);
        if (cached == null) {
            var id = Identifier.tryParse(effectId);
            cached = id == null ? null : commandBuildContext(server).lookupOrThrow(Registries.MOB_EFFECT)
                    .get(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
            if (cached == null) {
                LOGGER.warn("MCFC: unknown mob effect '{}' (the command will fail)", effectId);
                cached = PARSE_FAILED;
            }
            MOB_EFFECT_CACHE.put(effectId, cached);
        }
        @SuppressWarnings("unchecked")
        var effect = cached == PARSE_FAILED ? null : (Holder<net.minecraft.world.effect.MobEffect>) cached;
        return effect;
    }

    // -------------------------------------------------------------------------
    // Teleport commands — native compilation helpers
    // -------------------------------------------------------------------------

    public static void teleportToEntity(Entity target, Entity destination) {
        if (!(destination.level() instanceof ServerLevel destLevel)) return;
        performTeleport(target, destLevel, destination.getX(), destination.getY(), destination.getZ(),
                java.util.Set.of(), destination.getYRot(), destination.getXRot());
    }

    // `relFlags` for tp <pos> [<rot>]: which destination components were written relative (~ or ^),
    // exactly vanilla Coordinates.isX/Y/ZRelative (local ^ coordinates are relative on all axes).
    public static final int TP_REL_X = 1;
    public static final int TP_REL_Y = 2;
    public static final int TP_REL_Z = 4;
    public static final int TP_REL_YAW = 8;
    public static final int TP_REL_PITCH = 16;
    /** Set when a rotation argument is present (otherwise vanilla keeps the rotation: Relative.ROTATION). */
    public static final int TP_HAS_ROTATION = 32;
    private static final int TP_SAME_DIMENSION = 64;

    /** Vanilla TeleportCommand.getRelatives for every (relFlags, sameDimension) combination, built once. */
    private static final java.util.Set<?>[] TP_RELATIVES = new java.util.Set<?>[128];
    static {
        for (int flags = 0; flags < TP_RELATIVES.length; flags++) {
            boolean x = (flags & TP_REL_X) != 0, y = (flags & TP_REL_Y) != 0, z = (flags & TP_REL_Z) != 0;
            var relatives = net.minecraft.world.entity.Relative.union(
                    net.minecraft.world.entity.Relative.direction(x, y, z),
                    (flags & TP_SAME_DIMENSION) != 0 ? net.minecraft.world.entity.Relative.position(x, y, z) : java.util.Set.of(),
                    (flags & TP_HAS_ROTATION) == 0
                            ? net.minecraft.world.entity.Relative.ROTATION
                            : net.minecraft.world.entity.Relative.rotation((flags & TP_REL_YAW) != 0, (flags & TP_REL_PITCH) != 0));
            TP_RELATIVES[flags] = relatives.isEmpty()
                    ? java.util.Set.of()
                    : java.util.Collections.unmodifiableSet(java.util.EnumSet.copyOf(relatives));
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<net.minecraft.world.entity.Relative> tpRelatives(Entity target, ServerLevel world, int relFlags) {
        return (java.util.Set<net.minecraft.world.entity.Relative>) TP_RELATIVES[relFlags | (target.level().dimension() == world.dimension() ? TP_SAME_DIMENSION : 0)];
    }

    public static void teleportToPos(Entity target, ServerLevel world, Vec3 pos, int relFlags) {
        performTeleport(target, world, pos.x, pos.y, pos.z, tpRelatives(target, world, relFlags & ~TP_HAS_ROTATION), target.getYRot(), target.getXRot());
    }

    public static void teleportToPosRot(Entity target, ServerLevel world, Vec3 pos, float yaw, float pitch, int relFlags) {
        performTeleport(target, world, pos.x, pos.y, pos.z, tpRelatives(target, world, relFlags | TP_HAS_ROTATION), yaw, pitch);
    }

    /** Mirrors 26.3 TeleportCommand.performTeleport (without lookAt). Out-of-bounds destinations fail. */
    private static void performTeleport(Entity victim, ServerLevel level, double x, double y, double z, java.util.Set<net.minecraft.world.entity.Relative> relatives, float yRot, float xRot) {
        if (teleportVictim(victim, level, x, y, z, relatives, yRot, xRot)) {
            postTeleportCleanup(victim);
        }
    }

    /** The teleport part of performTeleport; the caller applies lookAt (if any) and then {@link #postTeleportCleanup}. */
    private static boolean teleportVictim(Entity victim, ServerLevel level, double x, double y, double z, java.util.Set<net.minecraft.world.entity.Relative> relatives, float yRot, float xRot) {
        if (!net.minecraft.world.level.Level.isInSpawnableBounds(net.minecraft.core.BlockPos.containing(x, y, z))) return false;
        double relX = relatives.contains(net.minecraft.world.entity.Relative.X) ? x - victim.getX() : x;
        double relY = relatives.contains(net.minecraft.world.entity.Relative.Y) ? y - victim.getY() : y;
        double relZ = relatives.contains(net.minecraft.world.entity.Relative.Z) ? z - victim.getZ() : z;
        float relYRot = relatives.contains(net.minecraft.world.entity.Relative.Y_ROT) ? yRot - victim.getYRot() : yRot;
        float relXRot = relatives.contains(net.minecraft.world.entity.Relative.X_ROT) ? xRot - victim.getXRot() : xRot;
        return victim.teleportTo(level, relX, relY, relZ, relatives, Mth.wrapDegrees(relYRot), Mth.wrapDegrees(relXRot), true);
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
        // Mirrors vanilla ClearInventoryCommands.clearInventory with maxCount -1 (never counting-only).
        int count = player.getInventory().clearOrCountMatchingItems(stack -> true, false, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return count;
    }

    public static int clearPlayerItem(ServerPlayer player, MinecraftServer server, String itemPredicate, int maxCount) {
        var parsed = parseItemPredicate(server, itemPredicate);
        if (parsed == null) return 0;
        // Mirrors vanilla ClearInventoryCommands.clearInventory: maxCount 0 counts only and skips the menu sync.
        boolean countingOnly = maxCount == 0;
        int count = player.getInventory().clearOrCountMatchingItems(parsed, countingOnly, maxCount, player.inventoryMenu.getCraftSlots());
        if (!countingOnly) {
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.slotsChanged(player.getInventory());
        }
        return count;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> ITEM_PREDICATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla ItemPredicateArgument (tags, `*`, component `=`/`~` predicates, `!`, `|`), or null when it does not parse. */
    private static java.util.function.@Nullable Predicate<net.minecraft.world.item.ItemStack> parseItemPredicate(MinecraftServer server, String itemString) {
        Object cached = ITEM_PREDICATE_CACHE.get(itemString);
        if (cached == null || (cached == PARSE_FAILED && failedParsesStale(server))) {
            var argument = net.minecraft.commands.arguments.item.ItemPredicateArgument.itemPredicate(commandBuildContext(server));
            cached = parseWhole("item predicate", itemString, argument::parse);
            if (cached == PARSE_FAILED) failedParsesStale(server);
            ITEM_PREDICATE_CACHE.put(itemString, cached);
        }
        @SuppressWarnings("unchecked")
        var predicate = cached == PARSE_FAILED ? null : (java.util.function.Predicate<net.minecraft.world.item.ItemStack>) cached;
        return predicate;
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

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> PARTICLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static net.minecraft.core.particles.@Nullable ParticleOptions parseParticle(MinecraftServer server, String particleName) {
        Object cached = PARTICLE_CACHE.get(particleName);
        if (cached == null) {
            var registries = commandBuildContext(server);
            cached = parseWhole("particle", particleName, reader -> net.minecraft.commands.arguments.ParticleArgument.readParticle(reader, registries));
            PARTICLE_CACHE.put(particleName, cached);
        }
        return cached == PARSE_FAILED ? null : (net.minecraft.core.particles.ParticleOptions) cached;
    }

    /** `particle <name> [<pos>]`: vanilla sends to every player of the level, count 0, not forced. */
    public static void spawnParticleSimple(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos) {
        spawnParticle(server, level, particleName, pos, Vec3.ZERO, 0.0, 0, false);
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
        teamModifyFull(server, teamName, option, value);
    }

    // -------------------------------------------------------------------------
    // Recipe commands — native compilation helpers
    // -------------------------------------------------------------------------

    // 26.3 RecipeCommand: single ids go through ResourceKeyArgument.key(RECIPE, r -> !r.isSpecial()),
    // so unknown AND special (incl. brewing) recipes fail without side effects; `*` uses
    // getLearnableRecipes(). Return values mirror vanilla's per-player awardRecipes/resetRecipes count.
    private static java.util.@Nullable List<net.minecraft.world.item.crafting.RecipeHolder<?>> learnableRecipe(MinecraftServer server, String recipeId) {
        var key = ResourceKey.create(Registries.RECIPE, cachedId(recipeId));
        var recipe = server.getRecipeManager().byKey(key);
        if (recipe.isEmpty() || recipe.get().value().isSpecial()) return null;
        return java.util.List.of(recipe.get());
    }

    public static int recipeGive(MinecraftServer server, ServerPlayer player, String recipeId) {
        var recipes = learnableRecipe(server, recipeId);
        return recipes == null ? 0 : player.awardRecipes(recipes);
    }

    public static int recipeTake(MinecraftServer server, ServerPlayer player, String recipeId) {
        var recipes = learnableRecipe(server, recipeId);
        return recipes == null ? 0 : player.resetRecipes(recipes);
    }

    public static int recipeGiveAll(MinecraftServer server, ServerPlayer player) {
        return player.awardRecipes(server.getRecipeManager().getLearnableRecipes());
    }

    public static int recipeTakeAll(MinecraftServer server, ServerPlayer player) {
        return player.resetRecipes(server.getRecipeManager().getLearnableRecipes());
    }

    /**
     * Vanilla ParticleCommand.sendParticles: {@code force} (`force` mode) raises the view distance to
     * 512 blocks; alwaysShow is always false. Without viewers vanilla targets every player, and the
     * per-player send keeps only those in this level; ServerLevel's broadcast overload does the same
     * over this level's players with one shared packet (speed on all axes, DEFAULT randomization).
     */
    public static void spawnParticle(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos, Vec3 delta, double speed, int count, boolean force) {
        var particle = parseParticle(server, particleName);
        if (particle == null) return;
        level.sendParticles(particle, force, false, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed);
    }

    public static void spawnParticleForPlayers(MinecraftServer server, ServerLevel level, String particleName, Vec3 pos, Vec3 delta, double speed, int count, boolean force, List<? extends Entity> viewers) {
        var particle = parseParticle(server, particleName);
        if (particle == null) return;
        for (var entity : viewers) {
            if (entity instanceof ServerPlayer player) {
                level.sendParticles(player, particle, force, false, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed);
            }
        }
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

    private static net.minecraft.world.scores.TeamColor teamColorByName(String name) {
        return net.minecraft.world.scores.TeamColor.byName(name);
    }

    private static final Pattern STRING_SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final DecimalFormat TAG_DECIMAL_FORMAT = Util.make(new DecimalFormat("#"), format -> {
        format.setMaximumFractionDigits(15);
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });


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

    /**
     * Runs an award loop like vanilla's advancement command: reward functions started meanwhile are queued
     * (ServerFunctionManagerMixin sees the context) and run once the loop has finished, not between two awards.
     */
    private static int awardDeferringRewards(MinecraftServer server, java.util.function.IntSupplier awards) {
        if (Commands.CURRENT_EXECUTION_CONTEXT.get() != null) return awards.getAsInt();
        int[] count = {0};
        Commands.executeCommandInContext(server.createCommandSourceStack(), _ -> count[0] = awards.getAsInt());
        return count[0];
    }

    public static int grantAdvancementOnly(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        return awardDeferringRewards(server, () -> {
            int count = 0;
            for (String criterion : holder.value().criteria().keySet()) {
                if (player.getAdvancements().award(holder, criterion)) count++;
            }
            return count;
        });
    }

    public static int grantAdvancementCriterion(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return 0;
        return awardDeferringRewards(server, () -> player.getAdvancements().award(holder, criterion) ? 1 : 0);
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
        return awardDeferringRewards(server, () -> {
            int count = 0;
            for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
                for (String criterion : holder.value().criteria().keySet()) {
                    if (player.getAdvancements().award(holder, criterion)) count++;
                }
            }
            return count;
        });
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
        return awardDeferringRewards(server, () -> {
            int count = 0;
            for (AdvancementHolder adv : collectTreeAdvancements(node, mode)) {
                for (String criterion : adv.value().criteria().keySet()) {
                    if (player.getAdvancements().award(adv, criterion)) count++;
                }
            }
            return count;
        });
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

    // Selector advancement checks (vanilla EntitySelectorOptions `advancements=`): an unknown
    // advancement or criterion fails the check whatever the expected value.

    public static boolean checkAdvancementDone(MinecraftServer server, ServerPlayer player, String id, boolean expected) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone() == expected;
    }

    public static boolean checkAdvancementCriterionDone(MinecraftServer server, ServerPlayer player, String id, String criterion, boolean expected) {
        AdvancementHolder holder = server.getAdvancements().get(cachedId(id));
        if (holder == null) return false;
        CriterionProgress progress = player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion);
        return progress != null && progress.isDone() == expected;
    }

    // Loot predicates by id, resolved once per reload (the reloadable registry lookup is replaced on /reload).
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> PREDICATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile net.minecraft.core.HolderLookup.@Nullable Provider predicateLookup;

    private static @Nullable LootItemCondition lootPredicate(MinecraftServer server, String predicateId) {
        var lookup = server.reloadableRegistries().lookup();
        if (lookup != predicateLookup) {
            PREDICATE_CACHE.clear();
            predicateLookup = lookup;
        }
        Object cached = PREDICATE_CACHE.get(predicateId);
        if (cached == null) {
            if (predicateId.startsWith("{")) {
                // Inline predicate (`execute if predicate {…}`): decoded with the registries like vanilla's ResourceOrIdArgument.
                cached = parseWhole("predicate", predicateId, reader -> {
                    var tag = net.minecraft.nbt.TagParser.parseCompoundAsArgument(reader);
                    var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess());
                    return LootItemCondition.DIRECT_CODEC.parse(ops, tag).getOrThrow(IllegalArgumentException::new);
                });
            } else {
                cached = lookup.get(ResourceKey.create(Registries.PREDICATE, cachedId(predicateId)))
                        .<Object>map(Holder::value).orElse(PARSE_FAILED);
            }
            PREDICATE_CACHE.put(predicateId, cached);
        }
        return cached == PARSE_FAILED ? null : (LootItemCondition) cached;
    }

    /** Selector `predicate=` / `predicate=!`: an unknown predicate never matches, even negated. */
    public static boolean checkEntityPredicate(MinecraftServer server, Entity entity, String predicateId, boolean expected) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var condition = lootPredicate(server, predicateId);
        if (condition == null) return false;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        ctx.pushVisitedElement(LootContext.createVisitedEntry(condition));
        return condition.test(ctx) == expected;
    }

    /** `execute if|unless predicate`: an unknown predicate fails both (vanilla ResourceOrIdArgument throws). */
    public static boolean checkCommandPredicate(MinecraftServer server, @Nullable Entity entity, ServerLevel level, Vec3 position, String predicateId, boolean expected) {
        var condition = lootPredicate(server, predicateId);
        if (condition == null) return false;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, position)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                .create(LootContextParamSets.COMMAND);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        ctx.pushVisitedElement(LootContext.createVisitedEntry(condition));
        return condition.test(ctx) == expected;
    }

    // Selector NBT check

    // -------------------------------------------------------------------------
    // Entity NBT matching
    //
    // Entity.saveWithoutId() is expensive (players especially), so patterns made only of
    // keys in matchSingleKey are answered from the entity API. Anything else serializes
    // fresh on every check, exactly like vanilla: no cross-command cache, so tp/tag/
    // effect/clear/... in the same tick are always visible.
    // -------------------------------------------------------------------------

    /**
     * Try to evaluate an NBT pattern against an entity WITHOUT serialization.
     * Returns {@code null} if the pattern contains keys not in the fast-path set
     * (caller should fall back to full serialization).
     *
     * <p>Every answer MUST equal {@code NbtUtils.compareNbt(pattern, saveWithoutId())}: keys the
     * entity does not write (26.3 {@code Entity.saveWithoutId} omits Tags when empty,
     * CustomNameVisible/Silent/NoGravity/Glowing when false, never writes MaxAir/MaxHealth/
     * FallDistance) never match, and tag types must match exactly.
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
                // Tags is only saved when non-empty; an empty expected list only matches an
                // empty actual list, so it can never match.
                if (tagList.isEmpty()) yield Boolean.FALSE;
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
                yield savedOnlyWhenTrue(entity.isCustomNameVisible(), expected);
            }
            case "Health" -> {
                if (!(value instanceof FloatTag expected)) yield null;
                // Only LivingEntity saves a float Health (ItemEntity saves a short).
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) yield Boolean.FALSE;
                yield Float.floatToIntBits(living.getHealth()) == Float.floatToIntBits(expected.floatValue());
            }
            // Never written by saveWithoutId (MaxHealth is an attribute, fall distance is
            // saved as `fall_distance`), so vanilla never matches these keys.
            case "MaxHealth", "MaxAir", "FallDistance" -> Boolean.FALSE;
            case "Air" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield (short) entity.getAirSupply() == expected.shortValue();
            }
            case "Fire" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield (short) entity.getRemainingFireTicks() == expected.shortValue();
            }
            case "OnGround" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield expected.byteValue() == (entity.onGround() ? 1 : 0);
            }
            case "NoGravity" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield savedOnlyWhenTrue(entity.isNoGravity(), expected);
            }
            case "Invulnerable" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                // NBT `Invulnerable` stores only the permanent flag; 26.3 isInvulnerable() also
                // counts temporary invulnerable_time.
                yield expected.byteValue() == (entity.isPermanentlyInvulnerable() ? 1 : 0);
            }
            case "Silent" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield savedOnlyWhenTrue(entity.isSilent(), expected);
            }
            case "Glowing" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield savedOnlyWhenTrue(entity.hasGlowingTag(), expected);
            }
            // BAC's check_paintings.mcfunction issues 47 distinct
            // `@e[type=painting,distance=..8,nbt={variant:"…"}]` selectors every
            // tick a player has placed a painting, hammering full-NBT
            // serialization. Comparing the painting's variant key directly is
            // ~50ns vs ~1ms for saveWithoutId + walk.
            case "variant" -> {
                if (!(value instanceof StringTag expected)) yield null;
                String expectedStr = expected.asString().orElse("");
                if (entity instanceof net.minecraft.world.entity.decoration.painting.Painting painting) {
                    var keyOpt = painting.getVariant().unwrapKey();
                    if (keyOpt.isEmpty()) yield Boolean.FALSE;
                    yield Boolean.valueOf(keyOpt.get().identifier().toString().equals(expectedStr));
                }
                yield null; // other entity types — let full path handle
            }
            // BAC checks `nbt={Item:{id:"minecraft:…"}}` on ItemEntities to
            // detect specific dropped items (eggs in splatfest etc). Compare
            // the item's registry id directly when the pattern is the simple
            // `{id:"…"}` shape.
            case "Item" -> {
                if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) yield null;
                if (!(value instanceof CompoundTag compound)) yield null;
                var idTag = compound.get("id");
                if (idTag == null || compound.size() != 1) yield null;
                if (!(idTag instanceof StringTag idStr)) yield null;
                // ItemEntity omits `Item` when its stack is empty.
                if (itemEntity.getItem().isEmpty()) yield Boolean.FALSE;
                String expectedId = idStr.asString().orElse("");
                var actual = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(itemEntity.getItem().getItem()).toString();
                yield actual.equals(expectedId);
            }
            // `full_stomach` uses `@a[nbt={foodLevel:20,foodSaturationLevel:20.0f}]`.
            // Player.getFoodData() exposes both without serialization.
            case "foodLevel" -> {
                if (!(entity instanceof net.minecraft.world.entity.player.Player player)) yield Boolean.FALSE;
                if (!(value instanceof IntTag expected)) yield null;
                yield player.getFoodData().getFoodLevel() == expected.intValue();
            }
            case "foodSaturationLevel" -> {
                if (!(entity instanceof net.minecraft.world.entity.player.Player player)) yield Boolean.FALSE;
                if (!(value instanceof FloatTag expected)) yield null;
                yield Float.floatToIntBits(player.getFoodData().getSaturationLevel()) == Float.floatToIntBits(expected.floatValue());
            }
            default -> null; // complex key — fall back to full serialization
        };
    }

    /** Flags written as {@code putBoolean(key, true)} only when set: only {@code 1b} can match. */
    private static boolean savedOnlyWhenTrue(boolean flag, ByteTag expected) {
        return flag && expected.byteValue() == 1;
    }

    public static CompoundTag getEntityNbt(Entity entity) {
        return net.minecraft.advancements.predicates.NbtPredicate.getEntityTagToCompare(entity);
    }

    // Vectors of Entity.saveWithoutId, in the order of CommandCompiler.ENTITY_VECTOR_KEYS.
    public static final int ENTITY_VECTOR_POS = 0;
    public static final int ENTITY_VECTOR_MOTION = 1;
    public static final int ENTITY_VECTOR_ROTATION = 2;

    /**
     * Element {@code index} (already resolved to 0..size-1) of the list {@code getEntityNbt(entity)} holds under Pos,
     * Motion or Rotation, read with the calls Entity.saveWithoutId makes: Pos is the vehicle's X/Z with the entity's
     * own Y while riding, Motion is getDeltaMovement() (overridden by shulkers), Rotation is [yRot, xRot] as floats.
     */
    public static Tag entityVectorElement(Entity entity, int vector, int index) {
        return switch (vector) {
            case ENTITY_VECTOR_POS -> {
                Entity vehicle = entity.getVehicle();
                yield DoubleTag.valueOf(switch (index) {
                    case 0 -> vehicle != null ? vehicle.getX() : entity.getX();
                    case 1 -> entity.getY();
                    default -> vehicle != null ? vehicle.getZ() : entity.getZ();
                });
            }
            case ENTITY_VECTOR_MOTION -> {
                Vec3 motion = entity.getDeltaMovement();
                yield DoubleTag.valueOf(index == 0 ? motion.x : index == 1 ? motion.y : motion.z);
            }
            default -> FloatTag.valueOf(index == 0 ? entity.getYRot() : entity.getXRot());
        };
    }

    public static boolean matchesEntityNbt(Entity entity, CompoundTag pattern) {
        // Fast-path: avoid saveWithoutId() for patterns using only known keys
        Boolean fast = tryMatchEntityNbtFast(entity, pattern);
        if (fast != null) return fast;
        return NbtUtils.compareNbt(pattern, getEntityNbt(entity), true);
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

    /**
     * Vanilla ScoreContents.resolve for a selector holder: no match → the selector source text as a name-only
     * holder; more than one → null (ERROR_NOT_SINGLE_ENTITY, the caller skips the command).
     */
    public static @Nullable Component getScoreText(MinecraftServer server, List<Entity> entities, String objective, String holderSource) {
        if (entities.size() > 1) return null;
        ScoreHolder holder = entities.isEmpty() ? ScoreHolder.forNameOnly(holderSource) : entities.getFirst();
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective obj = scoreboard.getObjective(objective);
        if (obj == null) return Component.empty();
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, obj);
        if (info == null) return Component.empty();
        return info.formatValue(obj.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.NO_STYLE));
    }

    // Give

    /** Parsed item strings (vanilla ItemArgument), including failures; dropped in onServerStopped(). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> ITEM_PARSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla ItemParser over the command build context (full `id[component=value,...]` syntax), or null when it does not parse. */
    private static net.minecraft.commands.arguments.item.@Nullable ItemInput parseItemInput(MinecraftServer server, String itemString) {
        Object cached = ITEM_PARSE_CACHE.get(itemString);
        if (cached == null || (cached == PARSE_FAILED && failedParsesStale(server))) {
            var parser = new net.minecraft.commands.arguments.item.ItemParser(commandBuildContext(server));
            cached = parseWhole("item", itemString, parser::parse);
            if (cached == PARSE_FAILED) failedParsesStale(server);
            ITEM_PARSE_CACHE.put(itemString, cached);
        }
        return cached == PARSE_FAILED ? null : (net.minecraft.commands.arguments.item.ItemInput) cached;
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
        // Vanilla GiveCommand fails (TOO_MANY_ITEMS) before giving anything above 100 stacks.
        if (count > maxStackSize * 100) return;

        // Mirrors vanilla GiveCommand.giveItem per-player loop.
        for (var entity : entities) {
            if (!(entity instanceof ServerPlayer player)) continue;
            int remaining = count;
            while (remaining > 0) {
                int size = Math.min(maxStackSize, remaining);
                remaining -= size;
                var copyToDrop = prototype.copyWithCount(size);
                boolean added = player.getInventory().add(copyToDrop);
                if (added && copyToDrop.isEmpty()) {
                    var drop = player.createItemStackToDrop(prototype.copy(), false, false);
                    if (drop != null) {
                        drop.makeFakeItem();
                        drop.level().addFreshEntity(drop);
                    }
                    player.level().playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                            net.minecraft.sounds.SoundSource.PLAYERS,
                            0.2F,
                            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                    );
                    player.containerMenu.broadcastChanges();
                } else {
                    var drop = player.createItemStackToDrop(copyToDrop, false, false);
                    if (drop != null) {
                        drop.setNoPickUpDelay();
                        drop.setTarget(player.getUUID());
                        drop.level().addFreshEntity(drop);
                    }
                }
            }
        }
    }

    // Playsound

    /** Direct sound-event holders per compile-time sound id (vanilla builds the same Holder.direct per call). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Holder<net.minecraft.sounds.SoundEvent>> SOUND_PARSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, net.minecraft.sounds.SoundSource> SOUND_SOURCE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    // Vanilla command parser accepts sound-source names like "player"/"block" (via
    // SoundSource.getName()), not the enum constant names ("PLAYERS"/"BLOCKS").
    // Match both forms so datapack strings stay valid at runtime.
    private static net.minecraft.sounds.SoundSource parseSoundSource(String name) {
        var cached = SOUND_SOURCE_CACHE.get(name);
        if (cached != null) return cached;
        net.minecraft.sounds.SoundSource found = null;
        for (var s : net.minecraft.sounds.SoundSource.values()) {
            if (s.getName().equalsIgnoreCase(name)) {
                found = s;
                break;
            }
        }
        if (found == null) found = net.minecraft.sounds.SoundSource.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        SOUND_SOURCE_CACHE.put(name, found);
        return found;
    }

    /**
     * Mirrors 26.3 PlaySoundCommand.playSound: one seed per command from the source level's
     * random, and only players in the source level hear it.
     */
    public static void playSoundToAll(ServerLevel level, List<? extends Entity> entities, String sound, String source, net.minecraft.world.phys.Vec3 pos, double volume, double pitch, double minVolume) {
        if (entities.isEmpty()) return;
        var soundHolder = SOUND_PARSE_CACHE.get(sound);
        if (soundHolder == null) {
            soundHolder = Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(sound)));
            SOUND_PARSE_CACHE.put(sound, soundHolder);
        }
        var soundSource = parseSoundSource(source);
        float vol = (float) volume;
        float pit = (float) pitch;
        float minVol = (float) minVolume;
        double maxDistSqr = Mth.square(soundHolder.value().getRange(vol));
        long seed = level.getRandom().nextLong();

        for (var entity : entities) {
            if (!(entity instanceof ServerPlayer player) || player.level() != level) continue;
            double dx = pos.x - player.getX();
            double dy = pos.y - player.getY();
            double dz = pos.z - player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSqr) {
                if (minVol <= 0.0f) continue;
                double d = Math.sqrt(distSq);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    soundHolder, soundSource, player.getX() + dx / d * 2.0, player.getY() + dy / d * 2.0, player.getZ() + dz / d * 2.0, minVol, pit, seed
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
        // Vanilla `summon <entity> [pos]`: finalize = true.
        createEntity(world, entityType, pos, new CompoundTag(), true);
    }

    public static void summonEntityNbt(MinecraftServer server, ServerLevel world, String entityType, Vec3 pos, CompoundTag nbt) {
        // Vanilla `summon <entity> <pos> <nbt>`: finalize = false, so the NBT is not overridden.
        // `nbt` is a shared, value-deduplicated compile-time constant: copy before createEntity adds `id`.
        createEntity(world, entityType, pos, nbt.copy(), false);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> ENTITY_TYPE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla ResourceArgument.getSummonableEntityType for a compile-time id, or null (unknown / not summonable). */
    private static net.minecraft.core.Holder.@Nullable Reference<net.minecraft.world.entity.EntityType<?>> summonableEntityType(MinecraftServer server, String entityType) {
        Object cached = ENTITY_TYPE_CACHE.get(entityType);
        if (cached == null) {
            var id = Identifier.tryParse(entityType);
            var type = id == null ? null : commandBuildContext(server).lookupOrThrow(Registries.ENTITY_TYPE)
                    .get(ResourceKey.create(Registries.ENTITY_TYPE, id)).orElse(null);
            if (type == null || !type.value().canSummon()) {
                LOGGER.warn("MCFC: '{}' is not a summonable entity type (the command will fail)", entityType);
                cached = PARSE_FAILED;
            } else {
                cached = type;
            }
            ENTITY_TYPE_CACHE.put(entityType, cached);
        }
        @SuppressWarnings("unchecked")
        var type = cached == PARSE_FAILED ? null : (net.minecraft.core.Holder.Reference<net.minecraft.world.entity.EntityType<?>>) cached;
        return type;
    }

    /**
     * Mirrors 26.3 SummonCommand.createEntity; {@code entityTag} is owned by this call (mutated).
     * Returns null where vanilla throws (unknown type, outside spawnable bounds, peaceful, load failure, duplicate UUID).
     */
    private static @Nullable Entity createEntity(ServerLevel level, String entityType, Vec3 pos, CompoundTag entityTag, boolean finalize) {
        var type = summonableEntityType(level.getServer(), entityType);
        if (type == null) return null;
        if (!net.minecraft.world.level.Level.isInSpawnableBounds(net.minecraft.core.BlockPos.containing(pos))) return null;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && !type.value().isAllowedInPeaceful()) return null;
        entityTag.putString("id", type.key().identifier().toString());
        Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
                entityTag,
                level,
                new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.COMMAND, false),
                e -> {
            e.snapTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
            return e;
        });
        if (entity == null) return null;
        if (finalize && entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
        }
        return level.tryAddFreshEntityWithPassengers(entity) ? entity : null;
    }

    // Time

    // Mutating time helpers mirror 26.3 TimeCommand: a no-op (already at that time/marker,
    // already paused/running, same rate), an unknown time marker, or a dimension without a
    // default clock throws in vanilla (MC-305707), i.e. the command fails without touching the
    // clock. Skipping the no-op matters: ServerClockManager.setTotalTicks resets partialTick and
    // every modifyClock broadcasts a time packet. Results use the compiled-function return
    // encoding (COMMAND_FAILED, or the vanilla result in the low 32 bits) so `time set 0` stays
    // distinguishable from a failure.

    private static @Nullable Holder<WorldClock> defaultClock(ServerLevel world) {
        return world.dimensionTypeRegistration().value().defaultClock().orElse(null);
    }

    private static long commandSuccess(int result) {
        return Integer.toUnsignedLong(result);
    }

    public static long setTime(MinecraftServer server, ServerLevel world, int time) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        ServerClockManager clockManager = server.clockManager();
        if (clockManager.getInstance(clock).totalTicks() == time) return COMMAND_FAILED;
        clockManager.setTotalTicks(clock, time);
        return commandSuccess(time);
    }

    public static long setTimeToMarker(MinecraftServer server, ServerLevel world, String timeMarkerId) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        ServerClockManager clockManager = server.clockManager();
        return switch (clockManager.moveToTimeMarker(clock, ResourceKey.create(ClockTimeMarkers.ROOT_ID, cachedId(timeMarkerId)))) {
            case NO_TIME_MARKER_FOUND, NOT_MOVED -> COMMAND_FAILED;
            case MOVED -> commandSuccess(TimeCommand.wrapTime(clockManager.getInstance(clock).totalTicks()));
        };
    }

    public static long addTime(MinecraftServer server, ServerLevel world, int time) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        ServerClockManager clockManager = server.clockManager();
        clockManager.addTicks(clock, time);
        return commandSuccess(TimeCommand.wrapTime(clockManager.getInstance(clock).totalTicks()));
    }

    public static long setTimeRate(MinecraftServer server, ServerLevel world, float rate) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        ServerClockManager clockManager = server.clockManager();
        if (clockManager.getInstance(clock).rate() == rate) return COMMAND_FAILED;
        clockManager.setRate(clock, rate);
        return commandSuccess(1);
    }

    public static long setTimePaused(MinecraftServer server, ServerLevel world, boolean paused) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        ServerClockManager clockManager = server.clockManager();
        if (clockManager.getInstance(clock).isPaused() == paused) return COMMAND_FAILED;
        clockManager.setPaused(clock, paused);
        return commandSuccess(1);
    }

    /** `time query time`: vanilla getDefaultClock fails (COMMAND_FAILED) in a dimension without a default clock. */
    public static long queryTime(MinecraftServer server, ServerLevel world) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return COMMAND_FAILED;
        return commandSuccess(TimeCommand.wrapTime(server.clockManager().getInstance(clock).totalTicks()));
    }

    /**
     * Vanilla TimeCommand timeline queries: fail (COMMAND_FAILED) when the dimension has no default clock,
     * the timeline is unknown, or it belongs to a different clock (ERROR_WRONG_TIMELINE_FOR_CLOCK).
     */
    private static @Nullable Timeline queryableTimeline(ServerLevel world, String timelineId) {
        Holder<WorldClock> clock = defaultClock(world);
        if (clock == null) return null;
        var timeline = world.registryAccess().get(ResourceKey.create(Registries.TIMELINE, cachedId(timelineId)));
        if (timeline.isEmpty() || !clock.equals(timeline.get().value().clock())) return null;
        return timeline.get().value();
    }

    public static long queryTimelineTicks(MinecraftServer server, ServerLevel world, String timelineId) {
        Timeline timeline = queryableTimeline(world, timelineId);
        if (timeline == null) return COMMAND_FAILED;
        return commandSuccess(TimeCommand.wrapTime(timeline.getCurrentTicks(server.clockManager())));
    }

    public static long queryTimelineRepetitions(MinecraftServer server, ServerLevel world, String timelineId) {
        Timeline timeline = queryableTimeline(world, timelineId);
        if (timeline == null) return COMMAND_FAILED;
        return commandSuccess(TimeCommand.wrapTime(timeline.getPeriodCount(server.clockManager())));
    }

    public static int queryGameTime(MinecraftServer server, ServerLevel world) {
        long gameTime = world.getGameTime();
        return TimeCommand.wrapTime(gameTime);
    }

    // Schedule

    public static void scheduleFunction(MinecraftServer server, String function, int time, boolean append) {
        // vanilla ScheduleCommand.schedule throws ERROR_SAME_TICK before removing or scheduling anything
        if (time == 0) return;
        var timerQueue = server.getScheduledEvents();
        long targetTime = server.overworld().getGameTime() + time;
        if (!append) {
            timerQueue.remove(function);
        }
        timerQueue.schedule(function, targetTime, new net.minecraft.world.level.timers.FunctionCallback(cachedId(function)));
    }

    public static int scheduleClear(MinecraftServer server, String function) {
        return server.getScheduledEvents().remove(function);
    }

    // Gamerule

    public static long setGamerule(MinecraftServer server, String rule, String value) {
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
        // Game rule ids in 26.1+ are unnamespaced (e.g. "mob_griefing"), but datapacks
        // often write `gamerule minecraft:mob_griefing false` and vanilla's command
        // parser accepts that form. Strip the default `minecraft:` namespace so we
        // compare against the rule's actual id — otherwise the set silently no-ops
        // and safe_explosion fails to suppress terrain damage.
        var byValue = GAMERULE_SET_CACHE.computeIfAbsent(rule, r -> new java.util.concurrent.ConcurrentHashMap<>());
        Object cached = byValue.get(value);
        if (cached == null) {
            var normalizedRule = rule.startsWith("minecraft:") ? rule.substring("minecraft:".length()) : rule;
            Object[] found = { PARSE_FAILED };
            server.getGameRules().visitGameRuleTypes(new net.minecraft.world.level.gamerules.GameRuleTypeVisitor() {
                @Override
                public <T> void visit(net.minecraft.world.level.gamerules.GameRule<T> gameRule) {
                    if (!normalizedRule.equals(gameRule.id())) return;
                    gameRule.deserialize(value).result().ifPresent(v -> found[0] = new GameruleAssignment<>(gameRule, v));
                }
            });
            cached = found[0];
            byValue.put(value, cached);
        }
        return cached == PARSE_FAILED ? COMMAND_FAILED : applyGamerule(server, (GameruleAssignment<?>) cached);
    }

    /** A parsed `gamerule <rule> <value>`: the rule and its deserialized (immutable) value. */
    private record GameruleAssignment<T>(net.minecraft.world.level.gamerules.GameRule<T> rule, T value) {}

    /** rule → value → GameruleAssignment, or PARSE_FAILED for an unknown rule or invalid value. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Object>> GAMERULE_SET_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static <T> long applyGamerule(MinecraftServer server, GameruleAssignment<T> assignment) {
        var gameRules = server.getGameRules();
        // 26.3 GameRuleCommand.setRule throws ERROR_GAME_RULE_NOT_SET (no set, no
        // onGameRuleChanged side effects) when the value is unchanged (MC-174958).
        if (gameRules.get(assignment.rule()).equals(assignment.value())) return COMMAND_FAILED;
        gameRules.set(assignment.rule(), assignment.value(), server);
        return commandSuccess(assignment.rule().getCommandResult(assignment.value()));
    }

    public static int queryGamerule(MinecraftServer server, String rule) {
        var normalizedRule = rule.startsWith("minecraft:") ? rule.substring("minecraft:".length()) : rule;
        var gameRules = server.overworld().getGameRules();
        int[] result = {0};
        gameRules.visitGameRuleTypes(new net.minecraft.world.level.gamerules.GameRuleTypeVisitor() {
            @Override
            public <T> void visit(net.minecraft.world.level.gamerules.GameRule<T> gameRule) {
                if (normalizedRule.equals(gameRule.id())) {
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

    /** Compiled-function return encoding of a failed command (success = 0, result = 0). */
    public static final long COMMAND_FAILED = 1L << 32;

    /**
     * Runs a vanilla command and returns its brigadier result in the compiled-function return
     * encoding: {@link #COMMAND_FAILED} on failure (parse error / thrown CommandSyntaxException,
     * i.e. vanilla's (false, 0) callback), otherwise the result in the low 32 bits.
     * {@code withCallback} replaces the inherited callback, as vanilla's ExecuteCommand task
     * reports through {@code source.callback().onResult(success, result)}.
     */
    public static long executeVanillaCommandWithResult(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, false);
        return chain == null ? COMMAND_FAILED : runFallbackWithResult(stack, command, chain);
    }

    public static long executeVanillaCommandWithResultDynamic(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, true);
        return chain == null ? COMMAND_FAILED : runFallbackWithResult(stack, command, chain);
    }

    /**
     * Runs a vanilla command that may execute {@code return} (directly, or after macro substitution). Returns
     * {@link VanillaFunctionCall#NO_RETURN} when it did not return, otherwise the frame return in compiled
     * encoding: {@code value} on success, {@code 1 << 32 | value} on failure. Vanilla runs a function line in
     * the function's frame, so its {@code return} leaves the function; the caller returns this value.
     */
    public static long executeVanillaCommandReturning(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, false);
        return chain == null ? VanillaFunctionCall.NO_RETURN : runFallbackReturning(stack, command, chain);
    }

    public static long executeVanillaCommandReturningDynamic(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, true);
        return chain == null ? VanillaFunctionCall.NO_RETURN : runFallbackReturning(stack, command, chain);
    }

    public static void executeVanillaCommand(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, false);
        if (chain != null) runFallback(stack, command, chain);
    }

    public static void executeVanillaCommandDynamic(CommandSourceStack stack, String command) {
        var chain = fallbackChain(stack.getServer(), command, true);
        if (chain != null) runFallback(stack, command, chain);
    }

    // Fallback lines are parsed ONCE, like vanilla parses function lines at load:
    // CommandFunction.parseCommand with a compilation source at the function-compilation
    // permission level (Commands.createCompilationContext), then the flattened ContextChain is
    // executed with the runtime source (BuildContexts.TopLevel) as Commands.performCommand does.
    // Constant command strings are cached for the dispatcher's lifetime (a /reload rebuilds the
    // dispatcher and drops the cache). Macro-substituted strings use a bounded LRU, the analogue
    // of vanilla MacroFunction's per-function instantiation cache.
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> FALLBACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DYNAMIC_FALLBACK_CACHE_SIZE = 512;
    private static final java.util.LinkedHashMap<String, Object> DYNAMIC_FALLBACK_CACHE = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Object> eldest) {
            return size() > DYNAMIC_FALLBACK_CACHE_SIZE;
        }
    };
    private static volatile com.mojang.brigadier.@Nullable CommandDispatcher<CommandSourceStack> fallbackDispatcher;
    /** Distinct failing fallback strings logged at WARN; later ones go to DEBUG (bounded log volume). */
    private static final int MAX_LOGGED_FALLBACK_FAILURES = 32;
    private static final java.util.concurrent.atomic.AtomicInteger loggedFallbackFailures = new java.util.concurrent.atomic.AtomicInteger();

    @SuppressWarnings("unchecked")
    private static com.mojang.brigadier.context.@Nullable ContextChain<CommandSourceStack> fallbackChain(MinecraftServer server, String command, boolean dynamic) {
        var dispatcher = server.getCommands().getDispatcher();
        if (dispatcher != fallbackDispatcher) {
            FALLBACK_CACHE.clear();
            synchronized (DYNAMIC_FALLBACK_CACHE) {
                DYNAMIC_FALLBACK_CACHE.clear();
            }
            fallbackDispatcher = dispatcher;
        }
        Object cached;
        if (dynamic) {
            synchronized (DYNAMIC_FALLBACK_CACHE) {
                cached = DYNAMIC_FALLBACK_CACHE.get(command);
                if (cached == null) {
                    cached = parseFallback(server, dispatcher, command);
                    DYNAMIC_FALLBACK_CACHE.put(command, cached);
                }
            }
        } else {
            cached = FALLBACK_CACHE.get(command);
            if (cached == null) {
                cached = parseFallback(server, dispatcher, command);
                FALLBACK_CACHE.put(command, cached);
            }
        }
        return cached == PARSE_FAILED ? null : (com.mojang.brigadier.context.ContextChain<CommandSourceStack>) cached;
    }

    private static void logFallbackFailure(String message, String command, Object detail) {
        if (loggedFallbackFailures.incrementAndGet() <= MAX_LOGGED_FALLBACK_FAILURES) {
            LOGGER.warn(message, command, detail);
        } else {
            LOGGER.debug(message, command, detail);
        }
    }

    private static Object parseFallback(MinecraftServer server, com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        String input = Commands.trimOptionalPrefix(command);
        var compilationSource = Commands.createCompilationContext(server.getFunctionCompilationPermissions());
        try {
            var parse = dispatcher.parse(input, compilationSource);
            Commands.validateParseResults(parse);
            Object chain = com.mojang.brigadier.context.ContextChain.tryFlatten(parse.getContext().build(input)).orElse(null);
            if (chain == null) {
                throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
            }
            return chain;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            // Vanilla would have refused to load the function; the line is a no-op failure here.
            logFallbackFailure("MCFC: vanilla fallback `{}` does not parse (the command fails): {}", command, e.getMessage());
            return PARSE_FAILED;
        }
    }

    private static long runFallbackWithResult(CommandSourceStack stack, String command, com.mojang.brigadier.context.ContextChain<CommandSourceStack> chain) {
        long[] out = { COMMAND_FAILED };
        runFallback(stack.withCallback((success, result) -> out[0] = success ? Integer.toUnsignedLong(result) : COMMAND_FAILED), command, chain);
        return out[0];
    }

    /** Receives the return of a fallback's top frame; {@code returned} is set when the frame was discarded. */
    private static final class FallbackReturn implements CommandResultCallback {
        boolean returned;
        long result = COMMAND_FAILED;

        @Override
        public void onResult(boolean success, int value) {
            result = success ? Integer.toUnsignedLong(value) : COMMAND_FAILED | Integer.toUnsignedLong(value);
        }
    }

    private static long runFallbackReturning(CommandSourceStack stack, String command, com.mojang.brigadier.context.ContextChain<CommandSourceStack> chain) {
        var frameReturn = new FallbackReturn();
        runFallback(stack, command, chain, frameReturn);
        // Every vanilla return path (return <value>|fail|run, FallthroughTask, a callee sharing the frame) discards
        // the frame. A discard without a reported value (a throwing modifier after `return run`) counts as failure.
        return frameReturn.returned ? frameReturn.result : VanillaFunctionCall.NO_RETURN;
    }

    private static void runFallback(CommandSourceStack stack, String command, com.mojang.brigadier.context.ContextChain<CommandSourceStack> chain) {
        runFallback(stack, command, chain, null);
    }

    private static void runFallback(CommandSourceStack stack, String command, com.mojang.brigadier.context.ContextChain<CommandSourceStack> chain, @Nullable FallbackReturn frameReturn) {
        // Vanilla's Commands.executeCommandInContext checks the thread-local
        // CURRENT_EXECUTION_CONTEXT: if non-null (we're already inside a
        // running command's ExecutionContext), it just queues the command in
        // the existing context's queue instead of running it synchronously.
        // Compiled code reads state RIGHT AFTER each fallback and expects its
        // effects to be visible, so null out the thread-local: vanilla builds a
        // fresh context, runs the command and tears it down; then restore the
        // outer context so further mixin-dispatched compiled calls still see it.
        var ctxField = Commands.CURRENT_EXECUTION_CONTEXT;
        var saved = ctxField.get();
        if (saved != null) ctxField.set(null);
        @SuppressWarnings("unchecked")
        ExecutionContext<CommandSourceStack>[] used = new ExecutionContext[1];
        try {
            Commands.executeCommandInContext(stack, context -> {
                used[0] = context;
                if (frameReturn == null) {
                    ExecutionContext.queueInitialCommandExecution(context, command, chain, stack, CommandResultCallback.EMPTY);
                } else {
                    // Same top frame as queueInitialCommandExecution in a fresh context, with an observable discard.
                    var discard = context.frameControlForDepth(0);
                    context.queueNext(new net.minecraft.commands.execution.CommandQueueEntry<>(
                        new net.minecraft.commands.execution.Frame(0, frameReturn, () -> {
                            frameReturn.returned = true;
                            discard.discard();
                        }),
                        new net.minecraft.commands.execution.tasks.BuildContexts.TopLevel<>(command, chain, stack)));
                }
            });
        } catch (CommandLimitAbort e) {
            throw e;
        } catch (Exception e) {
            // Same handling as Commands.performCommand.
            logFallbackFailure("MCFC: vanilla fallback `{}` threw: {}", command, e.toString());
            stack.sendFailure(Component.translatable("command.failed"));
        } finally {
            if (saved != null) ctxField.set(saved);
        }
        CommandLimitAbort.checkStopped(used[0]);
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

    // NBT text components: 26.3 NbtContents.resolve. Every tag the path selects in every
    // source (storage / each entity) is rendered with TextComponentTagVisitor (rich or plain),
    // or parsed as a component and resolved when `interpret` is set; results are joined with the
    // separator (vanilla default ", " without style, passed in by the compiler).

    public static Component getNbtText(net.minecraft.world.level.storage.CommandStorage storage, String storageName, String path, boolean plain, Component separator) {
        var nbtPath = nbtPath(path);
        if (nbtPath == null) return Component.empty();
        var tags = new ArrayList<Tag>();
        collectNbtTextTags(nbtPath, storage.get(cachedId(storageName)), tags);
        return renderNbtText(tags, plain, separator);
    }

    public static Component getNbtTextInterpreted(CommandSourceStack stack, net.minecraft.world.level.storage.CommandStorage storage, String storageName, String path, Component separator) {
        var nbtPath = nbtPath(path);
        if (nbtPath == null) return Component.empty();
        var tags = new ArrayList<Tag>();
        collectNbtTextTags(nbtPath, storage.get(cachedId(storageName)), tags);
        return interpretNbtText(stack, tags, separator);
    }

    public static Component getEntityNbtText(List<? extends Entity> entities, String path, boolean plain, Component separator) {
        var nbtPath = nbtPath(path);
        if (nbtPath == null || entities.isEmpty()) return Component.empty();
        var tags = new ArrayList<Tag>();
        for (var entity : entities) collectNbtTextTags(nbtPath, net.minecraft.advancements.predicates.NbtPredicate.getEntityTagToCompare(entity), tags);
        return renderNbtText(tags, plain, separator);
    }

    public static Component getEntityNbtTextInterpreted(CommandSourceStack stack, List<? extends Entity> entities, String path, Component separator) {
        var nbtPath = nbtPath(path);
        if (nbtPath == null || entities.isEmpty()) return Component.empty();
        var tags = new ArrayList<Tag>();
        for (var entity : entities) collectNbtTextTags(nbtPath, net.minecraft.advancements.predicates.NbtPredicate.getEntityTagToCompare(entity), tags);
        return interpretNbtText(stack, tags, separator);
    }

    private static void collectNbtTextTags(net.minecraft.commands.arguments.NbtPathArgument.NbtPath path, Tag root, List<Tag> out) {
        try {
            out.addAll(path.get(root));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            // vanilla: a source where the path matches nothing contributes no elements
        }
    }

    private static Component renderNbtText(List<Tag> tags, boolean plain, Component separator) {
        MutableComponent result = null;
        for (var tag : tags) {
            var visitor = new net.minecraft.nbt.TextComponentTagVisitor("", plain
                    ? net.minecraft.nbt.TextComponentTagVisitor.PlainStyling.INSTANCE
                    : net.minecraft.nbt.TextComponentTagVisitor.RichStyling.INSTANCE);
            var rendered = (MutableComponent) visitor.visit(tag);
            result = result == null ? rendered : result.append(separator).append(rendered);
        }
        return result == null ? Component.empty() : result;
    }

    private static Component interpretNbtText(CommandSourceStack stack, List<Tag> tags, Component separator) {
        if (tags.isEmpty()) return Component.empty();
        var registryOps = stack.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var context = net.minecraft.network.chat.ResolutionContext.create(stack);
        MutableComponent result = null;
        for (var tag : tags) {
            MutableComponent resolved;
            try {
                var component = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(registryOps, tag).getOrThrow();
                resolved = net.minecraft.network.chat.ComponentUtils.resolve(context, component);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse component: {}", tag, e);
                continue;
            }
            result = result == null ? resolved : result.append(separator).append(resolved);
        }
        return result == null ? Component.empty() : result;
    }

    // data get — vanilla DataCommands.getData / getNumeric

    /** `data get <target> <path>`: numbers are floored, collections/compounds give their size, strings their length. */
    public static int nbtTagToInt(Tag tag) {
        return switch (tag) {
            case net.minecraft.nbt.NumericTag numeric -> Mth.floor(numeric.doubleValue());
            case net.minecraft.nbt.CollectionTag collection -> collection.size();
            case CompoundTag compound -> compound.size();
            case StringTag(String value) -> value.length();
            default -> 0;
        };
    }

    public static boolean isNumericTag(Tag tag) {
        return tag instanceof net.minecraft.nbt.NumericTag;
    }

    /** `data get <target> <path> <scale>`: only numeric tags (callers jump to the failure path otherwise). */
    public static int nbtNumericToIntScaled(Tag tag, double scale) {
        return Mth.floor(((net.minecraft.nbt.NumericTag) tag).doubleValue() * scale);
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

    private static final java.util.concurrent.ConcurrentHashMap<String, ResourceKey<net.minecraft.world.level.Level>> DIMENSION_KEY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** `execute in`: one cached key + one map lookup (vanilla DimensionArgument.getDimension); null = unknown dimension. */
    public static net.minecraft.server.level.@Nullable ServerLevel getDimensionLevel(MinecraftServer server, String dimension) {
        var key = DIMENSION_KEY_CACHE.get(dimension);
        if (key == null) {
            key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
            DIMENSION_KEY_CACHE.put(dimension, key);
        }
        return server.getLevel(key);
    }

    /** `execute in`: vanilla CommandSourceStack.withLevel scales X/Z by the dimensions' teleportation scale. */
    public static net.minecraft.world.phys.Vec3 scalePositionForLevel(net.minecraft.server.level.ServerLevel from, net.minecraft.server.level.ServerLevel to, net.minecraft.world.phys.Vec3 pos) {
        if (from == to) return pos;
        double scale = net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
        return new net.minecraft.world.phys.Vec3(pos.x * scale, pos.y, pos.z * scale);
    }

    /** `execute if|unless loaded` (vanilla ExecuteCommand.isChunkLoaded). */
    public static boolean isBlockLoaded(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
        var chunkPos = net.minecraft.world.level.ChunkPos.containing(net.minecraft.core.BlockPos.containing(pos));
        var chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
        return chunk != null && chunk.getFullStatus() == net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING && level.areEntitiesLoaded(chunkPos.pack());
    }

    /** Biome id → its ResourceKey, or PARSE_FAILED when the biome registry has no such entry (registry is fixed per server). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> BIOME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** `execute if|unless biome`: 1 = biome matches, 0 = it does not, -1 = vanilla error so neither branch runs:
     * unknown biome id (ResourceOrTagArgument ERROR_UNKNOWN_RESOURCE), or position not loaded / outside the world
     * (BlockPosArgument.getLoadedBlockPos throws). */
    public static int checkBiome(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos, String biome) {
        Object key = BIOME_CACHE.get(biome);
        if (key == null) {
            var id = Identifier.tryParse(biome);
            var resourceKey = id == null ? null : ResourceKey.create(Registries.BIOME, id);
            key = resourceKey != null && level.registryAccess().lookupOrThrow(Registries.BIOME).get(resourceKey).isPresent() ? resourceKey : PARSE_FAILED;
            if (key == PARSE_FAILED) LOGGER.warn("MCFC: unknown biome '{}' (the command will fail)", biome);
            BIOME_CACHE.put(biome, key);
        }
        if (key == PARSE_FAILED) return -1;
        var blockPos = net.minecraft.core.BlockPos.containing(pos);
        if (!level.hasChunkAt(blockPos) || !level.isInWorldBounds(blockPos)) return -1;
        @SuppressWarnings("unchecked")
        var biomeKey = (ResourceKey<net.minecraft.world.level.biome.Biome>) key;
        return level.getBiome(blockPos).is(biomeKey) ? 1 : 0;
    }

    // -------------------------------------------------------------------------
    // Parse caches. Every compile-time constant string (block/item/particle/predicate) is parsed
    // once with vanilla's argument parser and cached, including failures (PARSE_FAILED, warned
    // once). Item/block failures are retried once after a reload (failedParsesStale). Holders
    // inside the results belong to the running server's registries, so every cache is dropped
    // in onServerStopped().
    // -------------------------------------------------------------------------

    private static final Object PARSE_FAILED = new Object();

    /** Same lookup vanilla builds the command dispatcher with (feature-filtered registries). */
    private static net.minecraft.commands.CommandBuildContext commandBuildContext(MinecraftServer server) {
        return net.minecraft.commands.CommandBuildContext.simple(server.registryAccess(), server.getWorldData().enabledFeatures());
    }

    @FunctionalInterface
    private interface StringParser {
        Object parse(com.mojang.brigadier.StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
    }

    /** Parses the whole string (trailing input is an error, as for a command argument) or returns PARSE_FAILED. */
    private static Object parseWhole(String kind, String input, StringParser parser) {
        try {
            var reader = new com.mojang.brigadier.StringReader(input);
            Object result = parser.parse(reader);
            if (reader.canRead()) {
                throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("MCFC: failed to parse {} '{}' (the command will fail): {}", kind, input, e.getMessage());
            return PARSE_FAILED;
        }
    }

    /** Reload generation the cached PARSE_FAILED entries were recorded in (the reloadable registry lookup is replaced on reload). */
    private static volatile @Nullable Object parseFailureGeneration;

    /**
     * Cached parse failures may become valid after `/reload` or `/datapack enable` (e.g. a `#pack:tag` that now
     * exists), where vanilla re-parses the functions. Only consulted on the failure path, so cached successes
     * (whose tag holder sets follow rebinding by key) cost nothing. Returns true, after dropping every cached
     * failure, when a reload happened since the failures were recorded.
     */
    private static synchronized boolean failedParsesStale(MinecraftServer server) {
        Object generation = server.reloadableRegistries().lookup();
        if (generation == parseFailureGeneration) return false;
        boolean hadGeneration = parseFailureGeneration != null;
        parseFailureGeneration = generation;
        if (!hadGeneration) return false;
        ITEM_PREDICATE_CACHE.values().removeIf(v -> v == PARSE_FAILED);
        ITEM_PARSE_CACHE.values().removeIf(v -> v == PARSE_FAILED);
        BLOCK_PREDICATE_CACHE.values().removeIf(v -> v == PARSE_FAILED);
        BLOCK_INPUT_CACHE.values().removeIf(v -> v == PARSE_FAILED);
        return true;
    }

    /** Called when the server stops: parse results hold that server's registry holders. */
    public static void onServerStopped() {
        BLOCK_PREDICATE_CACHE.clear();
        BLOCK_INPUT_CACHE.clear();
        ITEM_PARSE_CACHE.clear();
        ITEM_PREDICATE_CACHE.clear();
        PARTICLE_CACHE.clear();
        SOUND_PARSE_CACHE.clear();
        ENTITY_TYPE_CACHE.clear();
        NBT_PATH_CACHE.clear();
        FALLBACK_CACHE.clear();
        synchronized (DYNAMIC_FALLBACK_CACHE) {
            DYNAMIC_FALLBACK_CACHE.clear();
        }
        fallbackDispatcher = null;
        MOB_EFFECT_CACHE.clear();
        PREDICATE_CACHE.clear();
        predicateLookup = null;
        parseFailureGeneration = null;
        BIOME_CACHE.clear();
        GAMERULE_SET_CACHE.clear();
    }

    // Block checking для execute if block

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> BLOCK_PREDICATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla BlockPredicateArgument result for {@code predicate}, or null when it does not parse. */
    private static net.minecraft.commands.arguments.blocks.BlockPredicateArgument.@Nullable Result blockPredicate(MinecraftServer server, String predicate) {
        Object cached = BLOCK_PREDICATE_CACHE.get(predicate);
        if (cached == null || (cached == PARSE_FAILED && failedParsesStale(server))) {
            var blocks = commandBuildContext(server).lookupOrThrow(Registries.BLOCK);
            cached = parseWhole("block predicate", predicate, reader -> net.minecraft.commands.arguments.blocks.BlockPredicateArgument.parse(blocks, reader));
            if (cached == PARSE_FAILED) failedParsesStale(server);
            BLOCK_PREDICATE_CACHE.put(predicate, cached);
        }
        return cached == PARSE_FAILED ? null : (net.minecraft.commands.arguments.blocks.BlockPredicateArgument.Result) cached;
    }

    /** Vanilla BlockPosArgument.getLoadedBlockPos: the chunk must be loaded and the position inside the build height. */
    private static boolean isLoadedBlockPos(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.hasChunkAt(pos) && level.isInWorldBounds(pos);
    }

    /** {@code execute if|unless block} result: 1 matches, 0 does not match. */
    public static final int CHECK_BLOCK_MATCH = 1;
    public static final int CHECK_BLOCK_NO_MATCH = 0;
    /** Vanilla throws (unloaded chunk, outside the world, unparsable predicate): both `if` and `unless` fail. */
    public static final int CHECK_BLOCK_ERROR = -1;

    public static int checkBlock(net.minecraft.server.level.ServerLevel level, Vec3 position, String predicate) {
        var blockPos = net.minecraft.core.BlockPos.containing(position);
        if (!isLoadedBlockPos(level, blockPos)) return CHECK_BLOCK_ERROR;
        var parsed = blockPredicate(level.getServer(), predicate);
        if (parsed == null) return CHECK_BLOCK_ERROR;
        return parsed.test(new net.minecraft.world.level.block.state.pattern.BlockInWorld(level, blockPos, true)) ? CHECK_BLOCK_MATCH : CHECK_BLOCK_NO_MATCH;
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

    /**
     * Native `team modify` for the options the compiler validated (color incl. reset, friendlyFire,
     * seeFriendlyInvisibles, nametagVisibility, deathMessageVisibility, collisionRule). Component
     * options (displayName, prefix, suffix) need vanilla ComponentArgument resolution and are
     * compiled as vanilla fallbacks.
     */
    public static void teamModifyFull(MinecraftServer server, String teamName, String option, String value) {
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) return;
        switch (option) {
            case "color" -> {
                if (value.equals("reset")) {
                    team.setColor(Optional.empty());
                } else {
                    var color = teamColorByName(value);
                    if (color != null) team.setColor(Optional.of(color));
                }
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
            default -> LOGGER.warn("MCFC: team modify option '{}' has no native implementation", option);
        }
    }

    // -------------------------------------------------------------------------
    // Random commands — value, roll, reset (vanilla RandomCommand.randomSample; the compiler
    // rejects span 0 and span ≥ Integer.MAX_VALUE ranges, which vanilla fails)
    // -------------------------------------------------------------------------

    public static int randomValue(ServerLevel level, int min, int max) {
        return Mth.randomBetweenInclusive(level.getRandom(), min, max);
    }

    /** `random roll <range> <sequence>`: the result is announced to every player like vanilla. */
    public static int randomRollSequence(CommandSourceStack source, int min, int max, String sequenceId) {
        var server = source.getServer();
        int value = Mth.randomBetweenInclusive(server.getRandomSequence(cachedId(sequenceId)), min, max);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), value, min, max), false);
        return value;
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

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> BLOCK_INPUT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla BlockStateArgument (parseForBlock with NBT allowed), or null when it does not parse. */
    private static net.minecraft.commands.arguments.blocks.@Nullable BlockInput parseBlockInput(MinecraftServer server, String blockString) {
        Object cached = BLOCK_INPUT_CACHE.get(blockString);
        if (cached == null || (cached == PARSE_FAILED && failedParsesStale(server))) {
            var blocks = commandBuildContext(server).lookupOrThrow(Registries.BLOCK);
            cached = parseWhole("block state", blockString, reader -> {
                var result = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(blocks, reader, true);
                return new net.minecraft.commands.arguments.blocks.BlockInput(result.blockState(), result.properties().keySet(), result.nbt());
            });
            if (cached == PARSE_FAILED) failedParsesStale(server);
            BLOCK_INPUT_CACHE.put(blockString, cached);
        }
        return cached == PARSE_FAILED ? null : (net.minecraft.commands.arguments.blocks.BlockInput) cached;
    }

    /**
     * Mirrors 26.3 SetBlockCommand.setBlock; mode 0 = replace, 1 = keep, 2 = destroy.
     * Returns 1 on success, 0 where vanilla throws (unloaded/out of world, keep on non-air, unchanged block).
     */
    public static int setBlock(MinecraftServer server, ServerLevel level, Vec3 pos, String blockString, int mode) {
        var blockPos = net.minecraft.core.BlockPos.containing(pos);
        if (!isLoadedBlockPos(level, blockPos)) return 0;
        var input = parseBlockInput(server, blockString);
        if (input == null || level.isDebug()) return 0;
        if (mode == 1 && !level.isEmptyBlock(blockPos)) return 0;
        boolean placeNeeded = true;
        if (mode == 2) {
            level.destroyBlock(blockPos, true);
            placeNeeded = !input.getState().isAir() || !level.getBlockState(blockPos).isAir();
        }
        var oldState = level.getBlockState(blockPos);
        if (placeNeeded && !input.place(level, blockPos, 2 | 256)) return 0;
        level.updateNeighboursOnBlockSet(blockPos, oldState);
        return 1;
    }

    private static final net.minecraft.commands.arguments.blocks.BlockInput HOLLOW_CORE =
            new net.minecraft.commands.arguments.blocks.BlockInput(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), java.util.Collections.emptySet(), null);

    public static final int FILL_REPLACE = 0;
    public static final int FILL_REPLACE_FILTER = 1;
    public static final int FILL_KEEP = 2;
    public static final int FILL_DESTROY = 3;
    public static final int FILL_HOLLOW = 4;
    public static final int FILL_OUTLINE = 5;

    /**
     * Mirrors 26.3 FillCommand.fillBlocks (non-strict). Returns the vanilla count; 0 where vanilla
     * throws (unloaded/out-of-world corner, area above max_block_modifications, unparsable
     * block or filter, nothing changed).
     */
    public static int fillBlocks(MinecraftServer server, ServerLevel level, Vec3 from, Vec3 to, String blockString, int mode, @Nullable String filterString) {
        var fromPos = net.minecraft.core.BlockPos.containing(from);
        var toPos = net.minecraft.core.BlockPos.containing(to);
        if (!isLoadedBlockPos(level, fromPos) || !isLoadedBlockPos(level, toPos)) return 0;
        var input = parseBlockInput(server, blockString);
        if (input == null) return 0;
        net.minecraft.commands.arguments.blocks.BlockPredicateArgument.@Nullable Result filter = null;
        if (filterString != null) {
            filter = blockPredicate(server, filterString);
            if (filter == null) return 0;
        }
        var region = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(fromPos, toPos);
        long area = (long) region.getXSpan() * region.getYSpan() * region.getZSpan();
        if (area > level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MAX_BLOCK_MODIFICATIONS)) return 0;
        if (level.isDebug()) return 0;

        record UpdatedPosition(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState) {}
        var updatePositions = new java.util.ArrayList<UpdatedPosition>();
        int count = 0;
        for (var blockPos : net.minecraft.core.BlockPos.betweenClosed(
                region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ())) {
            if (filter != null && !filter.test(new net.minecraft.world.level.block.state.pattern.BlockInWorld(level, blockPos, true))) continue;
            if (mode == FILL_KEEP && !level.isEmptyBlock(blockPos)) continue;
            var oldState = level.getBlockState(blockPos);
            boolean affected = mode == FILL_DESTROY && level.destroyBlock(blockPos, true);
            net.minecraft.commands.arguments.blocks.@Nullable BlockInput block = input;
            if (mode == FILL_HOLLOW || mode == FILL_OUTLINE) {
                boolean interior = blockPos.getX() != region.minX() && blockPos.getX() != region.maxX()
                        && blockPos.getY() != region.minY() && blockPos.getY() != region.maxY()
                        && blockPos.getZ() != region.minZ() && blockPos.getZ() != region.maxZ();
                if (interior) block = mode == FILL_HOLLOW ? HOLLOW_CORE : null;
            }
            if (block != null && block.place(level, blockPos, 2 | 256)) {
                updatePositions.add(new UpdatedPosition(blockPos.immutable(), oldState));
                count++;
            } else if (affected) {
                count++;
            }
        }
        for (var updated : updatePositions) {
            level.updateNeighboursOnBlockSet(updated.pos(), updated.oldState());
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Teleport facing — tp with facing entity / coordinates
    // -------------------------------------------------------------------------

    /** `sourceAnchor` for the tp-facing helpers: the source stack's anchor known at compile time, or read from the stack. */
    public static final int TP_SOURCE_ANCHOR_FEET = 0;
    public static final int TP_SOURCE_ANCHOR_EYES = 1;
    public static final int TP_SOURCE_ANCHOR_FROM_STACK = 2;

    private static net.minecraft.commands.arguments.EntityAnchorArgument.Anchor sourceAnchor(CommandSourceStack stack, int sourceAnchor) {
        return switch (sourceAnchor) {
            case TP_SOURCE_ANCHOR_FEET -> net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
            case TP_SOURCE_ANCHOR_EYES -> net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES;
            default -> stack.getAnchor();
        };
    }

    /** Vanilla TeleportCommand.teleportToPos with LookAtPosition: keep rotation, teleport, then lookAt from the source anchor. */
    public static void teleportFacingPos(Entity target, ServerLevel world, Vec3 pos, int relFlags, CommandSourceStack stack, int sourceAnchor, Vec3 facingPos) {
        if (teleportVictim(target, world, pos.x, pos.y, pos.z, tpRelatives(target, world, relFlags & ~TP_HAS_ROTATION), target.getYRot(), target.getXRot())) {
            target.lookAt(sourceAnchor(stack, sourceAnchor), facingPos);
            postTeleportCleanup(target);
        }
    }

    /** Vanilla TeleportCommand.teleportToPos with LookAtEntity (players track the entity through ServerPlayer.lookAt). */
    public static void teleportFacingEntity(Entity target, ServerLevel world, Vec3 pos, int relFlags, CommandSourceStack stack, int sourceAnchor, Entity facingEntity, boolean useEyes) {
        if (teleportVictim(target, world, pos.x, pos.y, pos.z, tpRelatives(target, world, relFlags & ~TP_HAS_ROTATION), target.getYRot(), target.getXRot())) {
            var anchor = sourceAnchor(stack, sourceAnchor);
            var facingAnchor = useEyes ? net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES : net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
            if (target instanceof ServerPlayer player) {
                player.lookAt(anchor, facingEntity, facingAnchor);
            } else {
                target.lookAt(anchor, facingAnchor.apply(facingEntity));
            }
            postTeleportCleanup(target);
        }
    }

    // -------------------------------------------------------------------------
    // Data operations — merge entity, remove storage/entity, modify from/string
    // -------------------------------------------------------------------------

    /** Vanilla EntityDataAccessor.setData: reload the entity from {@code tag}, keeping its UUID. */
    private static void setEntityData(Entity entity, CompoundTag tag) {
        java.util.UUID uuid = entity.getUUID();
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            entity.load(net.minecraft.world.level.storage.TagValueInput.create(reporter, entity.registryAccess(), tag));
            entity.setUUID(uuid);
            entity.postDataManipulated();
        }
    }

    /** Vanilla DataCommands.mergeData: an unchanged merge fails without reloading the entity. */
    public static void dataMergeEntity(Entity entity, MinecraftServer server, CompoundTag patch) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        var old = net.minecraft.advancements.predicates.NbtPredicate.getEntityTagToCompare(entity);
        var merged = old.copy().merge(patch);
        if (old.equals(merged)) return;
        setEntityData(entity, merged);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> NBT_PATH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla NbtPathArgument for a compile-time constant path, or null when it does not parse. */
    private static net.minecraft.commands.arguments.NbtPathArgument.@Nullable NbtPath nbtPath(String path) {
        Object cached = NBT_PATH_CACHE.get(path);
        if (cached == null) {
            cached = parseWhole("NBT path", path, reader -> new net.minecraft.commands.arguments.NbtPathArgument().parse(reader));
            NBT_PATH_CACHE.put(path, cached);
        }
        return cached == PARSE_FAILED ? null : (net.minecraft.commands.arguments.NbtPathArgument.NbtPath) cached;
    }

    /** Vanilla DataCommands.removeData: nothing removed → the command fails and the target is not written back. */
    public static void dataRemoveStorage(net.minecraft.world.level.storage.CommandStorage storage, String storageName, String path) {
        var nbtPath = nbtPath(path);
        if (nbtPath == null) return;
        var id = cachedId(storageName);
        var data = storage.get(id);
        if (nbtPath.remove(data) == 0) return;
        storage.set(id, data);
    }

    public static void dataRemoveEntity(Entity entity, MinecraftServer server, String path) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        var nbtPath = nbtPath(path);
        if (nbtPath == null) return;
        var compound = net.minecraft.advancements.predicates.NbtPredicate.getEntityTagToCompare(entity);
        if (nbtPath.remove(compound) == 0) return;
        setEntityData(entity, compound);
    }

    // -------------------------------------------------------------------------
    // Execute summon / positioned over
    // -------------------------------------------------------------------------

    public static @Nullable Entity executeSummon(CommandSourceStack source, String entityTypeId) {
        // Vanilla ExecuteCommand.spawnEntityAndRedirect → SummonCommand.createEntity(..., new CompoundTag(), finalize = true)
        return createEntity(source.getLevel(), entityTypeId, source.getPosition(), new CompoundTag(), true);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, net.minecraft.world.level.levelgen.Heightmap.Types> HEIGHTMAP_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Vanilla `positioned over`: null (the branch fails) when the column's chunk is not loaded. */
    public static @Nullable Vec3 positionedOver(ServerLevel level, Vec3 pos, String heightmapType) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))) return null;
        var heightmap = HEIGHTMAP_CACHE.get(heightmapType);
        if (heightmap == null) {
            heightmap = net.minecraft.world.level.levelgen.Heightmap.Types.valueOf(heightmapType.toUpperCase(Locale.ROOT));
            HEIGHTMAP_CACHE.put(heightmapType, heightmap);
        }
        int height = level.getHeight(heightmap, Mth.floor(pos.x), Mth.floor(pos.z));
        return new Vec3(pos.x, height, pos.z);
    }

}
