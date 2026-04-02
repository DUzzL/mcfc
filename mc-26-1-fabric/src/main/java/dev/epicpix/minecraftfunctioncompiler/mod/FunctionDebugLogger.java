package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticKind;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FunctionDebugLogger {
    private FunctionDebugLogger() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("mcfc-debug");

    public static volatile boolean enabled = false;

    private static final ConcurrentHashMap<String, AtomicLong> totalCalls = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> vanillaCalls = new ConcurrentHashMap<>();
    private static volatile boolean mcfcWasOn = false;
    private static long lastFlushTick = 0;
    private static final long FLUSH_INTERVAL = 200;

    public enum CompileStatus {
        COMPILED        ("compiled   "),
        VANILLA_FALLBACK("vanilla    "),
        PARSE_ERROR     ("PARSE_ERR  "),
        COMPILE_ERROR   ("COMPILE_ERR"),
        EMITTER_ERROR   ("EMITTER_ERR"),
        LOAD_ERROR      ("LOAD_ERR   "),
        TOO_BIG         ("TOO_BIG    "),
        MCFC_OFF        ("mcfc_off   ");

        final String label;
        CompileStatus(String label) { this.label = label; }
    }

    public static void recordCall(Identifier id, boolean isVanilla) {
        if (!enabled) return;
        String key = id.toString();
        totalCalls.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        if (isVanilla) vanillaCalls.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public static void recordCompiledCall(Identifier id) {
        if (!enabled) return;
        setMcfcState(true);
        totalCalls.computeIfAbsent(id.toString(), k -> new AtomicLong()).incrementAndGet();
    }

    public static void tick(long currentTick) {
        if (!enabled) return;
        if (currentTick - lastFlushTick >= FLUSH_INTERVAL) {
            lastFlushTick = currentTick;
            flushToFile();
        }
    }

    public static void setMcfcState(boolean on) {
        if (on) mcfcWasOn = true;
    }

    private static Map<String, CompileStatus> buildStatusMap() {
        var loader = FunctionCompilerMain.functionClassLoader;
        var statusMap = new HashMap<String, CompileStatus>();
        if (loader == null) return statusMap;

        for (var diag : loader.getDiagnostics()) {
            if (diag.source() == null) continue;
            String key = diag.source().toString();
            CompileStatus status;
            if (diag.diagnosticId().equals(DiagnosticIds.FUNCTION_LOAD_TOO_BIG)) {
                status = CompileStatus.TOO_BIG;
            } else if (diag.kind() == DiagnosticKind.PARSE_ERROR) {
                status = CompileStatus.PARSE_ERROR;
            } else if (diag.kind() == DiagnosticKind.COMPILE_ERROR) {
                status = CompileStatus.COMPILE_ERROR;
            } else if (diag.kind() == DiagnosticKind.EMITTER_ERROR) {
                status = CompileStatus.EMITTER_ERROR;
            } else if (diag.kind() == DiagnosticKind.LOAD_ERROR) {
                status = CompileStatus.LOAD_ERROR;
            } else {
                continue;
            }
            statusMap.putIfAbsent(key, status);
        }
        return statusMap;
    }

    public static void flushToFile() {
        if (totalCalls.isEmpty()) return;

        var loader = FunctionCompilerMain.functionClassLoader;
        var statusMap = buildStatusMap();

        var rows = totalCalls.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(e ->
                vanillaCalls.getOrDefault(e.getKey(), new AtomicLong(0)).get()
            ).reversed())
            .map(entry -> {
                String id = entry.getKey();
                long total = entry.getValue().get();
                long vanilla = vanillaCalls.getOrDefault(id, new AtomicLong(0)).get();

                CompileStatus status;
                if (!mcfcWasOn) {
                    status = CompileStatus.MCFC_OFF;
                } else if (loader != null && loader.hasFunctionLoaded(Identifier.parse(id))) {
                    status = CompileStatus.COMPILED;
                } else if (statusMap.containsKey(id)) {
                    status = statusMap.get(id);
                } else {
                    status = CompileStatus.VANILLA_FALLBACK;
                }

                return new Object[]{id, total, vanilla, status};
            })
            .toList();

        int countCompiled = 0, countVanilla = 0, countError = 0, countTooBig = 0, countMcfcOff = 0;
        for (var row : rows) {
            switch ((CompileStatus) row[3]) {
                case COMPILED         -> countCompiled++;
                case VANILLA_FALLBACK -> countVanilla++;
                case MCFC_OFF         -> countMcfcOff++;
                case TOO_BIG          -> countTooBig++;
                default               -> countError++;
            }
        }

        var sb = new StringBuilder();
        sb.append("=== MCFC Function Debug Log ===\n");

        if (!mcfcWasOn) {
            sb.append("WARNING: MCFC был OFF — сделай /mcfc on, потом /mcfc debug reset + /mcfc debug on\n\n");
        }

        sb.append(String.format("Summary: %d compiled | %d vanilla | %d errors | %d too_big | %d mcfc_off%n%n",
            countCompiled, countVanilla, countError, countTooBig, countMcfcOff));

        sb.append(String.format("[%-11s] %-65s %8s %8s%n", "Status", "Function", "Total", "Vanilla"));
        sb.append("-".repeat(100)).append("\n");

        for (var row : rows) {
            String id    = (String) row[0];
            long total   = (long)   row[1];
            long vanilla = (long)   row[2];
            CompileStatus status = (CompileStatus) row[3];
            sb.append(String.format("[%s] %-65s %8d %8d%n", status.label, id, total, vanilla));
        }

        var notCalled = statusMap.entrySet().stream()
            .filter(e -> !totalCalls.containsKey(e.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .toList();

        if (!notCalled.isEmpty()) {
            sb.append("\n--- Ошибки компиляции в функциях которые не вызывались ---\n");
            for (var e : notCalled) {
                sb.append(String.format("[%s] %s%n", e.getValue().label, e.getKey()));
            }
        }

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
