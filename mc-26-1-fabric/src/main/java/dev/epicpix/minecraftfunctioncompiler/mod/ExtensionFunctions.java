package dev.epicpix.minecraftfunctioncompiler.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
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
            boolean first = false;

            for(var entity : entities) {
                if(!first) result.append(separator);

                result.append(entity.getDisplayName());
                first = true;
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

    public static CompoundTag getEntityNbt(Entity entity) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            return output.buildResult();
        }
    }

    public static boolean matchesEntityNbt(Entity entity, CompoundTag pattern) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            CompoundTag entityTag = output.buildResult();
            return NbtUtils.compareNbt(pattern, entityTag, true);
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

    public static void giveItems(MinecraftServer server, ServerPlayer player, String itemString) {
        String itemPart;
        int count = 1;

        int bracketDepth = 0;
        int lastSpaceOutside = -1;
        for (int i = 0; i < itemString.length(); i++) {
            char c = itemString.charAt(i);
            if (c == '[' || c == '{') bracketDepth++;
            else if (c == ']' || c == '}') bracketDepth--;
            else if (c == ' ' && bracketDepth == 0) lastSpaceOutside = i;
        }

        if (lastSpaceOutside > 0) {
            String tail = itemString.substring(lastSpaceOutside + 1);
            int parsed = parseInt(tail, Integer.MIN_VALUE);
            if (parsed != Integer.MIN_VALUE) {
                count = parsed;
                itemPart = itemString.substring(0, lastSpaceOutside);
            } else {
                itemPart = itemString;
            }
        } else {
            itemPart = itemString;
        }

        String itemId = itemPart.contains("[") ? itemPart.substring(0, itemPart.indexOf('[')) : itemPart;
        var itemOptional = server.registryAccess().lookupOrThrow(Registries.ITEM)
            .get(ResourceKey.create(Registries.ITEM, Identifier.parse(itemId)));
        if (itemOptional.isEmpty()) return;

        int remaining = count;
        var item = itemOptional.get();
        int maxStackSize = item.value().getDefaultMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStackSize);
            var stack = new net.minecraft.world.item.ItemStack(item, stackSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }

    private static int parseInt(String s, int defaultValue) {
        int result = 0;
        boolean negative = false;
        int i = 0;
        if (s.isEmpty()) return defaultValue;
        if (s.charAt(0) == '-') { negative = true; i = 1; }
        if (i >= s.length()) return defaultValue;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return defaultValue;
            result = result * 10 + (c - '0');
        }
        return negative ? -result : result;
    }

    // Playsound

    public static void playSound(ServerPlayer player, String sound, String source, net.minecraft.world.phys.Vec3 pos, double volume, double pitch, double minVolume) {
        var soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(sound));
        var soundSource = net.minecraft.sounds.SoundSource.valueOf(source.toUpperCase(java.util.Locale.ROOT));
        float dist = Math.max((float) volume, 1.0f) * 16.0f;
        double dx = pos.x - player.getX();
        double dy = pos.y - player.getY();
        double dz = pos.z - player.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > dist * dist) {
            if (minVolume <= 0.0) return;
            double d = Math.sqrt(distSq);
            double nx = player.getX() + dx / d * 2.0;
            double ny = player.getY() + dy / d * 2.0;
            double nz = player.getZ() + dz / d * 2.0;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(soundEvent), soundSource, nx, ny, nz, (float) minVolume, (float) pitch, player.level().getRandom().nextLong()
            ));
        } else {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(soundEvent), soundSource, pos.x, pos.y, pos.z, (float) volume, (float) pitch, player.level().getRandom().nextLong()
            ));
        }
    }

    // Summon

    public static void summonEntity(MinecraftServer server, ServerLevel world, String entityType, net.minecraft.world.phys.Vec3 pos) {
        summonEntityNbt(server, world, entityType, pos, new CompoundTag());
    }

    public static void summonEntityNbt(MinecraftServer server, ServerLevel world, String entityType, net.minecraft.world.phys.Vec3 pos, CompoundTag nbt) {
        nbt.putString("id", entityType);
        Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt, world, e -> {
            e.moveTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
            return e;
        });
        if (entity != null) {
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
            }
            world.tryAddFreshEntityWithPassengers(entity);
        }
    }

    // Time

    public static void setDayTime(MinecraftServer server, int time) {
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(time);
        }
    }

    public static void addDayTime(MinecraftServer server, int time) {
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(level.getDayTime() + time);
        }
    }

    public static int queryDayTime(MinecraftServer server) {
        return (int)(server.overworld().getDayTime() % 24000L);
    }

    public static int queryGameTime(MinecraftServer server) {
        return (int)(server.overworld().getGameTime() % 2147483647L);
    }

    public static int queryDay(MinecraftServer server) {
        return (int)(server.overworld().getDayTime() / 24000L);
    }

    // Schedule

    public static void scheduleFunction(MinecraftServer server, String function, int time, boolean append) {
        var id = Identifier.parse(function);
        var timer = server.overworld().getBlockTicks();
        long gameTime = server.overworld().getGameTime();
        var timers = server.getWorldData().overworldData().getScheduledEvents();
        if (!append) {
            timers.remove(id.toString());
        }
        timers.schedule(id.toString(), gameTime + time, new net.minecraft.world.level.timers.FunctionTagCallback(id));
    }

    public static void scheduleClear(MinecraftServer server, String function) {
        var id = Identifier.parse(function);
        server.getWorldData().overworldData().getScheduledEvents().remove(id.toString());
    }

    // Gamerule

    public static void setGamerule(MinecraftServer server, String rule, String value) {
        var rules = server.getGameRules();
        rules.visitGameRuleTypes(new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(net.minecraft.world.level.GameRules.Key<T> key, net.minecraft.world.level.GameRules.Type<T> type) {
                if (key.getId().equals(rule)) {
                    var val = rules.getRule(key);
                    val.deserialize(value);
                    val.onChanged(server);
                }
            }
        });
    }

    public static int queryGamerule(MinecraftServer server, String rule) {
        var rules = server.getGameRules();
        int[] result = {0};
        rules.visitGameRuleTypes(new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(net.minecraft.world.level.GameRules.Key<T> key, net.minecraft.world.level.GameRules.Type<T> type) {
                if (key.getId().equals(rule)) {
                    var val = rules.getRule(key);
                    if (val instanceof net.minecraft.world.level.GameRules.IntegerValue intVal) {
                        result[0] = intVal.get();
                    } else if (val instanceof net.minecraft.world.level.GameRules.BooleanValue boolVal) {
                        result[0] = boolVal.get() ? 1 : 0;
                    }
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

}