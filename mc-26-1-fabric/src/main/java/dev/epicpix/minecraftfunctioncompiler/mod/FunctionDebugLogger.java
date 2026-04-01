package dev.epicpix.minecraftfunctioncompiler.mod;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FunctionDebugLogger {
    private FunctionDebugLogger() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("mcfc-debug");

    public static volatile boolean enabled = false;

    private static final ConcurrentHashMap<String, AtomicLong> totalCalls = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> vanillaCalls = new ConcurrentHashMap<>();
    private static long lastFlushTick = 0;
    private static final long FLUSH_INTERVAL = 200;

    public static void recordCall(Identifier id, boolean isVanilla) {
        if (!enabled) return;
        String key = id.toString();
        totalCalls.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        if (isVanilla) vanillaCalls.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    // Вызывается только когда MCFC включён (isUseReady=true)
    public static void recordCompiledCall(Identifier id) {
        if (!enabled) return;
        totalCalls.computeIfAbsent(id.toString(), k -> new AtomicLong()).incrementAndGet();
    }

    public static void tick(long currentTick) {
        if (!enabled) return;
        if (currentTick - lastFlushTick >= FLUSH_INTERVAL) {
            lastFlushTick = currentTick;
            flushToFile();
        }
    }

    private static volatile boolean mcfcWasOn = false;

    public static void setMcfcState(boolean on) {
        if (on) mcfcWasOn = true;
    }

    public static void flushToFile() {
        if (totalCalls.isEmpty()) return;

        var sb = new StringBuilder();
        sb.append("=== MCFC Function Debug Log ===\n");
        if (!mcfcWasOn) {
            sb.append("WARNING: MCFC was OFF during this session! All calls show as vanilla.\n");
            sb.append("Run /mcfc on, then /mcfc debug reset and /mcfc debug on again.\n\n");
        }
        sb.append("Sorted by vanilla fallback calls (worst offenders first)\n\n");
        sb.append(String.format("%-70s %8s %8s%n", "Function", "Total", "Vanilla"));
        sb.append("-".repeat(90)).append("\n");

        totalCalls.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(e ->
                vanillaCalls.getOrDefault(e.getKey(), new AtomicLong(0)).get()
            ).reversed())
            .forEach(entry -> {
                String id = entry.getKey();
                long total = entry.getValue().get();
                long vanilla = vanillaCalls.getOrDefault(id, new AtomicLong(0)).get();
                sb.append(String.format("%-70s %8d %8d%n", id, total, vanilla));
            });

        Path path = Path.of("logs/mcfc-debug.log");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, sb.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to write mcfc debug log", e);
        }
    }

    public static void reset() {
        totalCalls.clear();
        vanillaCalls.clear();
        mcfcWasOn = false;
    }
}
