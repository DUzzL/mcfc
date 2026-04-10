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
import net.minecraft.world.timeline.Timeline;
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

    public static int grantAdvancementOnly(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
        if (holder == null) return 0;
        int count = 0;
        for (String criterion : holder.value().criteria().keySet()) {
            if (player.getAdvancements().award(holder, criterion)) count++;
        }
        return count;
    }

    public static int grantAdvancementCriterion(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
        if (holder == null) return 0;
        return player.getAdvancements().award(holder, criterion) ? 1 : 0;
    }

    public static int revokeAdvancementOnly(MinecraftServer server, ServerPlayer player, String id) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
        if (holder == null) return 0;
        int count = 0;
        for (String criterion : holder.value().criteria().keySet()) {
            if (player.getAdvancements().revoke(holder, criterion)) count++;
        }
        return count;
    }

    public static int revokeAdvancementCriterion(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
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
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
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
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
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
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
        if (holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static boolean isAdvancementCriterionDone(MinecraftServer server, ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(id));
        if (holder == null) return false;
        CriterionProgress progress = player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion);
        return progress != null && progress.isDone();
    }

    // Selector predicate check

    public static boolean testEntityPredicate(MinecraftServer server, Entity entity, String predicateId) {
        var lookup = server.reloadableRegistries().lookup().lookup(Registries.PREDICATE);
        if (lookup.isEmpty()) return false;
        var holder = lookup.get().get(ResourceKey.create(Registries.PREDICATE, Identifier.parse(predicateId)));
        if (holder.isEmpty()) return false;
        LootItemCondition condition = holder.get().value();
        ServerLevel level = (ServerLevel) entity.level();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        return condition.test(ctx);
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
        var server = entity.level() instanceof ServerLevel sl ? sl.getServer() : null;
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
     *
     * Supported keys: Tags, CustomName, CustomNameVisible, Health, MaxHealth,
     * Air, MaxAir, Fire, FallDistance, OnGround, NoGravity, Invulnerable,
     * Silent, Glowing.
     */
    private static @Nullable Boolean tryMatchEntityNbtFast(Entity entity, CompoundTag pattern) {
        for (String key : pattern.keySet()) {
            Boolean keyResult = matchSingleKey(entity, key, pattern.get(key));
            if (keyResult == null) return null;   // unknown key — need full serialization
            if (!keyResult) return Boolean.FALSE;  // known key, value didn't match
        }
        return Boolean.TRUE;
    }

    private static @Nullable Boolean matchSingleKey(Entity entity, String key, net.minecraft.nbt.Tag value) {
        return switch (key) {
            case "Tags" -> {
                if (!(value instanceof ListTag tagList)) yield null;
                var entityTags = entity.entityTags();
                for (int i = 0; i < tagList.size(); i++) {
                    var t = tagList.get(i);
                    if (!(t instanceof StringTag st)) yield null;
                    if (!entityTags.contains(st.asString().orElse(""))) yield Boolean.FALSE;
                }
                yield Boolean.TRUE;
            }
            case "CustomName" -> {
                // Component.Serializer.toJson removed in MC 26.1 — fall back to full serialization
                yield null;
            }
            case "CustomNameVisible" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.isCustomNameVisible() == (expected.byteValue() != 0));
            }
            case "Health" -> {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) yield Boolean.FALSE;
                if (!(value instanceof FloatTag expected)) yield null;
                yield Boolean.valueOf(living.getHealth() == expected.floatValue());
            }
            case "MaxHealth" -> {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) yield Boolean.FALSE;
                if (!(value instanceof FloatTag expected)) yield null;
                yield Boolean.valueOf(living.getMaxHealth() == expected.floatValue());
            }
            case "Air" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield Boolean.valueOf(entity.getAirSupply() == expected.shortValue());
            }
            case "MaxAir" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield Boolean.valueOf(entity.getMaxAirSupply() == expected.shortValue());
            }
            case "Fire" -> {
                if (!(value instanceof ShortTag expected)) yield null;
                yield Boolean.valueOf(entity.getRemainingFireTicks() == expected.shortValue());
            }
            case "FallDistance" -> {
                if (!(value instanceof FloatTag expected)) yield null;
                yield Boolean.valueOf((float) entity.fallDistance == expected.floatValue());
            }
            case "OnGround" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.onGround() == (expected.byteValue() != 0));
            }
            case "NoGravity" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.isNoGravity() == (expected.byteValue() != 0));
            }
            case "Invulnerable" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.isInvulnerable() == (expected.byteValue() != 0));
            }
            case "Silent" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.isSilent() == (expected.byteValue() != 0));
            }
            case "Glowing" -> {
                if (!(value instanceof ByteTag expected)) yield null;
                yield Boolean.valueOf(entity.hasGlowingTag() == (expected.byteValue() != 0));
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
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            entityNbtTickCache.putAll(tempResults);
        }
    }



    // Text building

    public static MutableComponent createTranslatableComponent(String key, List<Component> args) {
        return Component.translatable(key, args.toArray());
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
     *
     * The input is expected to be just the item descriptor — count is now a
     * separate argument passed explicitly to giveItemsToAll, so no trailing
     * space/number splitting is needed here.
     *
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
     *
     * Previously every call paid for Identifier.parse + SoundEvent.
     * createVariableRangeEvent + Holder.direct + String.toUpperCase +
     * SoundSource.valueOf, even when the same playsound ran thousands of
     * times from a tick function.
     */
    public static void playSoundToAll(List<? extends Entity> entities, String sound, String source, net.minecraft.world.phys.Vec3 pos, double volume, double pitch, double minVolume) {
        if (entities.isEmpty()) return;

        String cacheKey = sound + '\0' + source;
        var parsed = SOUND_PARSE_CACHE.get(cacheKey);
        if (parsed == null) {
            var soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(sound));
            var holder = net.minecraft.core.Holder.direct(soundEvent);
            var soundSource = net.minecraft.sounds.SoundSource.valueOf(source.toUpperCase(java.util.Locale.ROOT));
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
            world.tryAddFreshEntityWithPassengers(entity);
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
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();
        Holder<Timeline> timeline = world.registryAccess().get(
                ResourceKey.create(Registries.TIMELINE, Identifier.parse(timelineId))
        ).get();

        long currentTicks = timeline.value().getCurrentTicks(clockManager);
        return TimeCommand.wrapTime(currentTicks);
    }

    public static int queryTimelineRepetitions(MinecraftServer server, ServerLevel world, String timelineId) {
        ServerClockManager clockManager = server.clockManager();
        Holder<WorldClock> clock = world.dimensionTypeRegistration().value().defaultClock().get();
        Holder<Timeline> timeline = world.registryAccess().get(
                ResourceKey.create(Registries.TIMELINE, Identifier.parse(timelineId))
        ).get();

        long repetitions = timeline.value().getPeriodCount(clockManager);
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
        for (int i = 0; i < list.size(); i++) {
            Tag element = list.get(i);
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
        net.minecraft.nbt.Tag current = compound;
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
            net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
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

    public static boolean checkBlock(net.minecraft.server.level.ServerLevel level, Vec3 position, String predicate) {
        var blockPos = net.minecraft.core.BlockPos.containing(position);
        var state = level.getBlockState(blockPos);
        if (predicate.startsWith("#")) {
            var id = Identifier.parse(predicate.substring(1));
            var tagKey = net.minecraft.tags.TagKey.create(Registries.BLOCK, id);
            return state.is(tagKey);
        }
        String blockId = predicate.contains("[") ? predicate.substring(0, predicate.indexOf('[')) : predicate;
        var optBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId));
        return optBlock.isPresent() && state.is(optBlock.get());
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

}