package dev.dpdump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.timers.TimerQueue;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Test tooling: {@code /dpdump write <name>} dumps deterministic world state as canonical JSON. */
public final class DpDumpCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String MOD_VERSION = "1.0.0";

    private static volatile Config config = Config.defaults();

    private DpDumpCommand() {
    }

    // ------------------------------------------------------------------ config

    record Box(String name, String dimension, int[] from, int[] to) {
    }

    record Config(List<Box> boxes, int maxEntities, boolean generateMissingChunks) {
        static Config defaults() {
            return new Config(List.of(new Box("spawn", "minecraft:overworld", new int[]{-48, -64, -48}, new int[]{47, 127, 47})), 3000, false);
        }
    }

    private static Path workDir() {
        return Path.of("").toAbsolutePath();
    }

    static Config loadConfig() throws Exception {
        Path file = workDir().resolve("dpdump.json");
        if (!Files.isRegularFile(file)) return Config.defaults();
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Config def = Config.defaults();
        List<Box> boxes = new ArrayList<>();
        if (root.has("boxes")) {
            for (JsonElement e : root.getAsJsonArray("boxes")) {
                JsonObject o = e.getAsJsonObject();
                int[] a = intTriple(o.getAsJsonArray("from"));
                int[] b = intTriple(o.getAsJsonArray("to"));
                int[] lo = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
                int[] hi = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
                String dim = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
                boxes.add(new Box(o.get("name").getAsString(), dim, lo, hi));
            }
        } else {
            boxes = def.boxes();
        }
        int max = root.has("maxEntities") ? root.get("maxEntities").getAsInt() : def.maxEntities();
        boolean gen = root.has("generateMissingChunks") ? root.get("generateMissingChunks").getAsBoolean() : def.generateMissingChunks();
        return new Config(boxes, max, gen);
    }

    private static int[] intTriple(JsonArray a) {
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
    }

    private static void reloadConfigQuiet() {
        try {
            config = loadConfig();
        } catch (Throwable t) {
            System.err.println("[dpdump] failed to read dpdump.json, using defaults: " + t);
            config = Config.defaults();
        }
    }

    // ----------------------------------------------------------------- command

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        reloadConfigQuiet();
        var base = Commands.literal("dpdump").requires(Commands.hasPermission(Commands.LEVEL_OWNERS));
        base.then(Commands.literal("write").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            String name = StringArgumentType.getString(ctx, "name");
            try {
                if (name.contains("..") || name.isEmpty()) throw new IllegalArgumentException("bad name");
                byte[] bytes = dump(src.getServer());
                Path dir = src.getServer().getServerDirectory().toAbsolutePath().resolve("dumps");
                Files.createDirectories(dir);
                Path target = dir.resolve(name + ".json");
                Path tmp = dir.resolve(name + ".json.tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                String msg = "dpdump: wrote dumps/" + name + ".json (" + bytes.length + " bytes)";
                src.sendSuccess(() -> Component.literal(msg), false);
                return 1;
            } catch (Throwable t) {
                String msg = "dpdump: error " + t.getClass().getSimpleName() + ": " + t.getMessage();
                src.sendFailure(Component.literal(msg));
                return 0;
            }
        })));
        base.then(Commands.literal("config").then(Commands.literal("reload").executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            try {
                config = loadConfig();
                String msg = "dpdump: config reloaded (" + config.boxes().size() + " boxes, maxEntities " + config.maxEntities()
                        + ", generateMissingChunks " + config.generateMissingChunks() + ")";
                src.sendSuccess(() -> Component.literal(msg), false);
                return 1;
            } catch (Throwable t) {
                src.sendFailure(Component.literal("dpdump: error " + t.getClass().getSimpleName() + ": " + t.getMessage()));
                return 0;
            }
        })));
        dispatcher.register(base);
    }

    // -------------------------------------------------------------------- dump

    private static byte[] dump(MinecraftServer server) {
        Config cfg = config;
        JsonObject root = new JsonObject();
        // Blocks run first: they may generate chunks, which the entity listing should then see.
        section(root, "blocks", () -> blocks(server, cfg));
        section(root, "world", () -> world(server));
        section(root, "gamerules", () -> gamerules(server));
        section(root, "scoreboard", () -> scoreboard(server));
        section(root, "storage", () -> storage(server));
        section(root, "bossbars", () -> bossbars(server));
        section(root, "players", () -> players(server));
        section(root, "entities", () -> entities(server, cfg));
        section(root, "functions", () -> functions(server));
        section(root, "functionTags", () -> functionTags(server));
        section(root, "scheduled", () -> scheduled(server));
        section(root, "datapacks", () -> datapacks(server));
        JsonObject meta = new JsonObject();
        meta.addProperty("mod", "dpdump");
        meta.addProperty("modVersion", MOD_VERSION);
        meta.addProperty("mcVersion", server.getServerVersion());
        meta.addProperty("timestampless", true);
        root.add("meta", meta);
        return (GSON.toJson(canonical(root)) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void section(JsonObject root, String key, Supplier<JsonElement> body) {
        try {
            root.add(key, body.get());
        } catch (Throwable t) {
            root.add(key, errorObject(t));
        }
    }

    private static JsonObject errorObject(Throwable t) {
        JsonObject o = new JsonObject();
        o.addProperty("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        return o;
    }

    /** Returns a deep copy where every object has its keys sorted alphabetically. */
    private static JsonElement canonical(JsonElement e) {
        if (e.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var en : e.getAsJsonObject().entrySet()) sorted.put(en.getKey(), canonical(en.getValue()));
            JsonObject out = new JsonObject();
            sorted.forEach(out::add);
            return out;
        }
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement c : e.getAsJsonArray()) out.add(canonical(c));
            return out;
        }
        return e;
    }

    // ----------------------------------------------------------------- helpers

    private static JsonPrimitive num(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return new JsonPrimitive(Double.toString(v));
        double scale = Math.pow(10, decimals);
        double r = Math.round(v * scale) / scale;
        if (r == 0.0) r = 0.0; // normalize negative zero
        return new JsonPrimitive(r);
    }

    private static JsonArray strings(java.util.Collection<String> values) {
        JsonArray a = new JsonArray();
        new TreeSet<>(values).forEach(a::add);
        return a;
    }

    private static String enumName(Object o) {
        if (o == null) return null;
        if (o instanceof StringRepresentable s) return s.getSerializedName();
        if (o instanceof Enum<?> e) return e.name().toLowerCase(Locale.ROOT);
        return o.toString();
    }

    private static String componentJson(MinecraftServer server, Component c) {
        if (c == null) return null;
        JsonElement j = ComponentSerialization.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), c).getOrThrow();
        return COMPACT.toJson(canonical(j));
    }

    /** Canonical SNBT: compound keys sorted, everything else printed by the tag's own toString. */
    static String snbt(Tag tag) {
        StringBuilder sb = new StringBuilder();
        snbt(tag, sb);
        return sb.toString();
    }

    private static void snbt(Tag tag, StringBuilder sb) {
        if (tag instanceof CompoundTag c) {
            sb.append('{');
            boolean first = true;
            for (String k : new TreeSet<>(c.keySet())) {
                if (!first) sb.append(',');
                first = false;
                if (k.matches("[A-Za-z0-9._+-]+")) sb.append(k);
                else sb.append(StringTag.valueOf(k));
                sb.append(':');
                snbt(c.get(k), sb);
            }
            sb.append('}');
        } else if (tag instanceof ListTag l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                snbt(l.get(i), sb);
            }
            sb.append(']');
        } else {
            sb.append(tag.toString());
        }
    }

    private static String id(Identifier id) {
        return id == null ? null : id.toString();
    }

    private static String sha256(byte[] data) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    private static List<ServerLevel> levels(MinecraftServer server) {
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) out.add(l);
        out.sort(Comparator.comparing(l -> l.dimension().identifier().toString()));
        return out;
    }

    // ------------------------------------------------------------------ world

    private static JsonElement world(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        JsonObject o = new JsonObject();
        o.addProperty("gameTime", overworld.getGameTime());
        o.addProperty("dayTime", overworld.getDefaultClockTime());
        o.addProperty("raining", overworld.isRaining());
        o.addProperty("thundering", overworld.isThundering());
        o.addProperty("difficulty", enumName(server.getWorldData().getDifficulty()));
        var respawn = server.getRespawnData();
        JsonObject spawn = new JsonObject();
        BlockPos sp = respawn.pos();
        spawn.add("pos", ints(sp.getX(), sp.getY(), sp.getZ()));
        spawn.addProperty("dimension", id(respawn.dimension().identifier()));
        o.add("spawn", spawn);
        var wb = overworld.getWorldBorder();
        JsonObject border = new JsonObject();
        border.add("center", new JsonArray());
        border.getAsJsonArray("center").add(num(wb.getCenterX(), 3));
        border.getAsJsonArray("center").add(num(wb.getCenterZ(), 3));
        border.add("size", num(wb.getSize(), 3));
        o.add("worldborder", border);
        return o;
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int i : v) a.add(i);
        return a;
    }

    private static JsonElement gamerules(MinecraftServer server) {
        var rules = server.getGameRules();
        JsonObject o = new JsonObject();
        rules.availableRules().forEach(rule -> o.addProperty(rule.id(), rules.getAsString(rule)));
        return o;
    }

    // ------------------------------------------------------------- scoreboard

    private static JsonElement scoreboard(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        JsonObject root = new JsonObject();

        JsonObject objectives = new JsonObject();
        for (Objective ob : sb.getObjectives()) {
            JsonObject o = new JsonObject();
            o.addProperty("criteria", ob.getCriteria().getName());
            o.addProperty("displayName", componentJson(server, ob.getDisplayName()));
            o.addProperty("renderType", ob.getRenderType().getId());
            objectives.add(ob.getName(), o);
        }
        root.add("objectives", objectives);

        JsonObject scores = new JsonObject();
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            JsonObject per = new JsonObject();
            for (var e : sb.listPlayerScores(holder).object2IntEntrySet()) {
                per.addProperty(e.getKey().getName(), e.getIntValue());
            }
            scores.add(holder.getScoreboardName(), per);
        }
        root.add("scores", scores);

        JsonObject slots = new JsonObject();
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective ob = sb.getDisplayObjective(slot);
            if (ob != null) slots.addProperty(slot.getSerializedName(), ob.getName());
        }
        root.add("displaySlots", slots);

        JsonObject teams = new JsonObject();
        for (PlayerTeam t : sb.getPlayerTeams()) {
            JsonObject o = new JsonObject();
            o.addProperty("displayName", componentJson(server, t.getDisplayName()));
            o.addProperty("color", t.getColor().map(DpDumpCommand::enumName).orElse(null));
            o.addProperty("prefix", componentJson(server, t.getPlayerPrefix()));
            o.addProperty("suffix", componentJson(server, t.getPlayerSuffix()));
            o.addProperty("friendlyFire", t.isAllowFriendlyFire());
            o.addProperty("seeFriendlyInvisibles", t.canSeeFriendlyInvisibles());
            o.addProperty("nametagVisibility", enumName(t.getNameTagVisibility()));
            o.addProperty("collisionRule", enumName(t.getCollisionRule()));
            o.addProperty("deathMessageVisibility", enumName(t.getDeathMessageVisibility()));
            o.add("members", strings(t.getPlayers()));
            teams.add(t.getName(), o);
        }
        root.add("teams", teams);
        return root;
    }

    private static JsonElement storage(MinecraftServer server) {
        var st = server.getCommandStorage();
        JsonObject o = new JsonObject();
        List<Identifier> keys = new ArrayList<>();
        st.keys().forEach(keys::add);
        for (Identifier key : keys) o.addProperty(key.toString(), snbt(st.get(key)));
        return o;
    }

    private static JsonElement bossbars(MinecraftServer server) {
        JsonObject o = new JsonObject();
        for (var ev : server.getCustomBossEvents().getEvents()) {
            JsonObject b = new JsonObject();
            b.addProperty("name", componentJson(server, ev.getName()));
            b.addProperty("value", ev.value());
            b.addProperty("max", ev.max());
            b.addProperty("color", enumName(ev.getColor()));
            b.addProperty("overlay", enumName(ev.getOverlay()));
            b.addProperty("style", enumName(ev.getOverlay()));
            b.addProperty("visible", ev.isVisible());
            b.addProperty("darkenScreen", ev.shouldDarkenScreen());
            b.addProperty("playBossMusic", ev.shouldPlayBossMusic());
            b.addProperty("createWorldFog", ev.shouldCreateWorldFog());
            List<String> names = new ArrayList<>();
            for (ServerPlayer p : ev.getPlayers()) names.add(p.getScoreboardName());
            b.add("players", strings(names));
            o.add(ev.customId().toString(), b);
        }
        return o;
    }

    // ---------------------------------------------------------------- players

    private static JsonElement players(MinecraftServer server) {
        JsonObject out = new JsonObject();
        List<ServerPlayer> list = new ArrayList<>(server.getPlayerList().getPlayers());
        list.sort(Comparator.comparing(ServerPlayer::getScoreboardName));
        for (ServerPlayer p : list) {
            JsonElement el;
            try {
                el = player(server, p);
            } catch (Throwable t) {
                el = errorObject(t);
            }
            out.add(p.getScoreboardName(), el);
        }
        return out;
    }

    private static JsonElement player(MinecraftServer server, ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", p.getUUID().toString());
        JsonArray pos = new JsonArray();
        pos.add(num(p.getX(), 3));
        pos.add(num(p.getY(), 3));
        pos.add(num(p.getZ(), 3));
        o.add("pos", pos);
        JsonArray rot = new JsonArray();
        rot.add(num(p.getYRot(), 3));
        rot.add(num(p.getXRot(), 3));
        o.add("rot", rot);
        o.addProperty("dimension", id(p.level().dimension().identifier()));
        o.addProperty("gameMode", p.gameMode().getName());
        o.add("health", num(p.getHealth(), 3));
        o.addProperty("food", p.getFoodData().getFoodLevel());
        o.addProperty("xpLevel", p.experienceLevel);
        o.addProperty("xpTotal", p.totalExperience);
        o.add("tags", strings(p.entityTags()));
        o.addProperty("selectedSlot", p.getInventory().getSelectedSlot());

        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        JsonArray inv = new JsonArray();
        var inventory = p.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) addStack(inv, i, inventory.getItem(i), ops);
        o.add("inventory", inv);
        JsonArray ender = new JsonArray();
        var chest = p.getEnderChestInventory();
        for (int i = 0; i < chest.getContainerSize(); i++) addStack(ender, i, chest.getItem(i), ops);
        o.add("enderChest", ender);

        // Advancements
        var adv = p.getAdvancements();
        TreeSet<String> done = new TreeSet<>();
        TreeMap<String, TreeSet<String>> criteria = new TreeMap<>();
        int recipeDone = 0;
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            String advId = holder.id().toString();
            boolean recipe = holder.id().getPath().startsWith("recipes/");
            var progress = adv.getOrStartProgress(holder);
            if (recipe) {
                if (progress.isDone()) recipeDone++;
                continue;
            }
            if (progress.isDone()) done.add(advId);
            TreeSet<String> got = new TreeSet<>();
            for (String c : progress.getCompletedCriteria()) got.add(c);
            if (!got.isEmpty()) criteria.put(advId, got);
        }
        o.add("advancementsDone", strings(done));
        JsonObject crit = new JsonObject();
        criteria.forEach((k, v) -> crit.add(k, strings(v)));
        o.add("advancementCriteria", crit);
        o.addProperty("recipeAdvancementsDone", recipeDone);

        List<JsonObject> effects = new ArrayList<>();
        for (var eff : p.getActiveEffects()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", eff.getEffect().getRegisteredName());
            e.addProperty("amplifier", eff.getAmplifier());
            e.addProperty("duration", eff.getDuration());
            effects.add(e);
        }
        effects.sort(Comparator.comparing((JsonObject e) -> e.get("id").getAsString())
                .thenComparingInt(e -> e.get("amplifier").getAsInt())
                .thenComparingInt(e -> e.get("duration").getAsInt()));
        JsonArray ea = new JsonArray();
        effects.forEach(ea::add);
        o.add("effects", ea);
        return o;
    }

    private static void addStack(JsonArray into, int slot, ItemStack stack, net.minecraft.resources.RegistryOps<Tag> ops) {
        if (stack.isEmpty()) return;
        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        CompoundTag c = (CompoundTag) tag;
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        o.addProperty("id", c.getStringOr("id", ""));
        o.addProperty("count", stack.getCount());
        Tag comps = c.get("components");
        o.addProperty("components", comps == null ? "{}" : snbt(comps));
        into.add(o);
    }

    // --------------------------------------------------------------- entities

    private static final Set<String> DROPPED_ENTITY_KEYS = Set.of(
            "uuid", "motion", "falldistance", "fire", "air", "onground", "portalcooldown", "hurttime",
            "hurtbytimestamp", "brain", "leash", "pos", "rotation", "ticksfrozen", "tickssincelasthurtbymob");

    private static String normKey(String k) {
        return k.toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static boolean dropEntityKey(String key) {
        String n = normKey(key);
        return DROPPED_ENTITY_KEYS.contains(n) || n.startsWith("lasthurtby");
    }

    private record EntityRow(String type, double x, double y, double z, Entity entity) {
    }

    private static JsonElement entities(MinecraftServer server, Config cfg) {
        JsonObject out = new JsonObject();
        for (ServerLevel level : levels(server)) {
            TreeMap<String, Integer> counts = new TreeMap<>();
            List<EntityRow> rows = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Player || e.isRemoved()) continue;
                String type = EntityType.getKey(e.getType()).toString();
                counts.merge(type, 1, Integer::sum);
                rows.add(new EntityRow(type, round(e.getX(), 2), round(e.getY(), 2), round(e.getZ(), 2), e));
            }
            rows.sort(Comparator.comparing(EntityRow::type).thenComparingDouble(EntityRow::x)
                    .thenComparingDouble(EntityRow::y).thenComparingDouble(EntityRow::z));
            boolean truncated = rows.size() > cfg.maxEntities();
            if (truncated) rows = new ArrayList<>(rows.subList(0, cfg.maxEntities()));

            record Built(EntityRow row, String nbt, JsonObject json) {
            }
            List<Built> built = new ArrayList<>();
            for (EntityRow r : rows) {
                Entity e = r.entity();
                JsonObject j = new JsonObject();
                j.addProperty("type", r.type());
                j.addProperty("uuid", e.getUUID().toString());
                JsonArray pos = new JsonArray();
                pos.add(num(r.x(), 2));
                pos.add(num(r.y(), 2));
                pos.add(num(r.z(), 2));
                j.add("pos", pos);
                j.add("tags", strings(e.entityTags()));
                j.addProperty("customName", componentJson(server, e.getCustomName()));
                String nbt;
                try (var reporter = new ProblemReporter.ScopedCollector(e.problemPath(), org.slf4j.LoggerFactory.getLogger("dpdump"))) {
                    TagValueOutput vo = TagValueOutput.createWithContext(reporter, e.registryAccess());
                    e.saveWithoutId(vo);
                    CompoundTag tag = vo.buildResult();
                    for (String k : new ArrayList<>(tag.keySet())) {
                        if (dropEntityKey(k)) tag.remove(k);
                    }
                    nbt = snbt(tag);
                } catch (Throwable t) {
                    nbt = "error:" + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
                j.addProperty("nbt", nbt);
                built.add(new Built(r, nbt, j));
            }
            built.sort(Comparator.comparing((Built b) -> b.row().type()).thenComparingDouble(b -> b.row().x())
                    .thenComparingDouble(b -> b.row().y()).thenComparingDouble(b -> b.row().z())
                    .thenComparing(Built::nbt)
                    .thenComparing(b -> b.json().get("uuid").getAsString()));
            JsonArray list = new JsonArray();
            built.forEach(b -> list.add(b.json()));

            JsonObject lv = new JsonObject();
            JsonObject cj = new JsonObject();
            counts.forEach(cj::addProperty);
            lv.add("counts", cj);
            lv.add("list", list);
            lv.addProperty("truncated", truncated);
            out.add(level.dimension().identifier().toString(), lv);
        }
        return out;
    }

    private static double round(double v, int decimals) {
        double s = Math.pow(10, decimals);
        double r = Math.round(v * s) / s;
        return r == 0.0 ? 0.0 : r;
    }

    // ----------------------------------------------------------------- blocks

    private static JsonElement blocks(MinecraftServer server, Config cfg) {
        JsonArray out = new JsonArray();
        for (Box box : cfg.boxes()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", box.name());
            o.addProperty("dimension", box.dimension());
            o.add("from", ints(box.from()));
            o.add("to", ints(box.to()));
            try {
                blockBox(server, cfg, box, o);
            } catch (Throwable t) {
                o.addProperty("error", t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            out.add(o);
        }
        return out;
    }

    private static void blockBox(MinecraftServer server, Config cfg, Box box, JsonObject o) throws Exception {
        ServerLevel level = null;
        for (ServerLevel l : levels(server)) {
            if (l.dimension().identifier().toString().equals(box.dimension())) level = l;
        }
        if (level == null) throw new IllegalStateException("dimension not loaded: " + box.dimension());

        int minX = box.from()[0], minY = Math.max(box.from()[1], level.getMinY()), minZ = box.from()[2];
        int maxX = box.to()[0], maxY = Math.min(box.to()[1], level.getMaxY()), maxZ = box.to()[2];
        int cMinX = minX >> 4, cMaxX = maxX >> 4, cMinZ = minZ >> 4, cMaxZ = maxZ >> 4;
        int w = cMaxX - cMinX + 1, d = cMaxZ - cMinZ + 1;

        LevelChunk[] chunks = new LevelChunk[w * d];
        MessageDigest[] colDigests = new MessageDigest[w * d];
        List<String> ungenerated = new ArrayList<>();
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                LevelChunk chunk = cfg.generateMissingChunks() ? level.getChunk(cx, cz) : level.getChunkSource().getChunkNow(cx, cz);
                int idx = (cx - cMinX) * d + (cz - cMinZ);
                chunks[idx] = chunk;
                colDigests[idx] = MessageDigest.getInstance("SHA-256");
                if (chunk == null) ungenerated.add(cx + "," + cz);
            }
        }

        MessageDigest all = MessageDigest.getInstance("SHA-256");
        IdentityHashMap<BlockState, byte[]> stateBytes = new IdentityHashMap<>();
        HashMap<Long, byte[]> beBytes = new HashMap<>();
        byte[] nl = {'\n'};
        long nonAir = 0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        // Block entity snapshots, once per chunk.
        Map<Integer, Map<Long, BlockEntity>> beByChunk = new HashMap<>();
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i] != null && !chunks[i].getBlockEntities().isEmpty()) {
                Map<Long, BlockEntity> m = new HashMap<>();
                chunks[i].getBlockEntities().forEach((bp, be) -> m.put(bp.asLong(), be));
                beByChunk.put(i, m);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int idx = ((x >> 4) - cMinX) * d + ((z >> 4) - cMinZ);
                LevelChunk chunk = chunks[idx];
                if (chunk == null) continue;
                MessageDigest col = colDigests[idx];
                Map<Long, BlockEntity> bes = beByChunk.get(idx);
                for (int y = minY; y <= maxY; y++) {
                    mp.set(x, y, z);
                    BlockState st = chunk.getBlockState(mp);
                    byte[] sb = stateBytes.get(st);
                    if (sb == null) {
                        sb = st.toString().getBytes(StandardCharsets.UTF_8);
                        stateBytes.put(st, sb);
                    }
                    all.update(sb);
                    col.update(sb);
                    if (!st.isAir()) nonAir++;
                    if (bes != null) {
                        BlockEntity be = bes.get(mp.asLong());
                        if (be != null) {
                            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                            tag.remove("x");
                            tag.remove("y");
                            tag.remove("z");
                            byte[] bb = ("|be:" + snbt(tag)).getBytes(StandardCharsets.UTF_8);
                            all.update(bb);
                            col.update(bb);
                        }
                    }
                    all.update(nl);
                    col.update(nl);
                }
            }
        }

        o.addProperty("hash", hex(all.digest()));
        JsonObject columns = new JsonObject();
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                int idx = (cx - cMinX) * d + (cz - cMinZ);
                if (chunks[idx] != null) columns.addProperty(cx + "," + cz, hex(colDigests[idx].digest()));
            }
        }
        o.add("columns", columns);
        o.addProperty("nonAirCount", nonAir);
        JsonArray ung = new JsonArray();
        ungenerated.stream().sorted(Comparator.comparingInt((String s) -> Integer.parseInt(s.split(",")[0]))
                .thenComparingInt(s -> Integer.parseInt(s.split(",")[1]))).forEach(ung::add);
        o.add("ungenerated", ung);
    }

    // -------------------------------------------------------------- functions

    private static JsonElement functions(MinecraftServer server) {
        TreeSet<String> ids = new TreeSet<>();
        for (Identifier id : server.getFunctions().getFunctionNames()) ids.add(id.toString());
        return strings(ids);
    }

    private static JsonElement functionTags(MinecraftServer server) {
        JsonObject o = new JsonObject();
        for (Identifier tag : server.getFunctions().getTagNames()) {
            TreeSet<String> members = new TreeSet<>();
            for (var f : server.getFunctions().getTag(tag)) members.add(f.id().toString());
            o.add(tag.toString(), strings(members));
        }
        return o;
    }

    private static JsonElement scheduled(MinecraftServer server) {
        TimerQueue<MinecraftServer> q = server.getScheduledEvents();
        JsonElement j = TimerQueue.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), q).getOrThrow();
        return j;
    }

    private static JsonElement datapacks(MinecraftServer server) {
        JsonArray a = new JsonArray();
        server.getPackRepository().getSelectedIds().forEach(a::add);
        return a;
    }
}
