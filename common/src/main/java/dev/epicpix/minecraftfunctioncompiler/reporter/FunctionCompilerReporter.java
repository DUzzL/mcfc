package dev.epicpix.minecraftfunctioncompiler.reporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ENABLE_REPORTING;
import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.isDevelopmentEnvironment;
import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.versionCommit;
import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.versionName;

public final class FunctionCompilerReporter {
    private FunctionCompilerReporter() {}

    private enum ReportingStatus {
        DISABLED_BY_COMPILE_CONSTANTS,
        DISABLED_BY_UNSUPPORTED_VERSION,
        DISABLED_BY_CONFIG,
        ENABLED,
    }

    private static volatile ReportingStatus dataReportingStatus = ENABLE_REPORTING ? ReportingStatus.ENABLED : ReportingStatus.DISABLED_BY_COMPILE_CONSTANTS;

    public static void setReportingEnabled(boolean enable) {
        if(dataReportingStatus == ReportingStatus.ENABLED && !enable) {
            dataReportingStatus = ReportingStatus.DISABLED_BY_CONFIG;
        }else if(dataReportingStatus == ReportingStatus.DISABLED_BY_CONFIG && enable) {
            dataReportingStatus = ReportingStatus.ENABLED;
        }
    }

    private static final UUID runUuid = UUID.randomUUID();
    private static final URI REPORT_URI = URI.create("https://mcfc.mods.epicpix.dev/api/v1/send_reports");
    private static final java.time.Duration HTTP_TIMEOUT = java.time.Duration.ofSeconds(10);
    private static final java.util.concurrent.ExecutorService SENDER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "Function Compiler Data Reporter");
        t.setDaemon(true);
        return t;
    });
    // SENDER only serializes submissions. It must not also be the client's executor: the synchronous send()
    // below runs on SENDER and waits for exchange stages the client schedules on its executor, which would
    // deadlock the single thread. The default executor uses its own daemon threads.
    private static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

    @Nullable
    private static String minecraftVersionName = null;
    @Nullable
    private static String minecraftVersionType = null;
    @Nullable
    private static MinecraftEnvironment minecraftEnvironment = null;
    private static boolean isTesting = false;

    public static void setMinecraftVersion(String version, String versionType, MinecraftEnvironment environment) {
        if(minecraftVersionName != null) throw new RuntimeException("Tried to set minecraft version twice");
        MinecraftVersion.setCurrentVersion(MinecraftVersion.getByName(version));
        minecraftVersionName = version;
        minecraftVersionType = versionType;
        minecraftEnvironment = environment;
    }

    public static void markTestEnvironment() {
        isTesting = true;
    }

    public static boolean isTestEnvironment() {
        return isTesting;
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<JsonObject> reportQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @SuppressWarnings("ConstantValue")
    public static void reportRawData(String type, JsonElement data) {
        if(!ENABLE_REPORTING || dataReportingStatus != ReportingStatus.ENABLED) return;
        if(minecraftVersionName == null || minecraftVersionType == null || minecraftEnvironment == null) return;
        if(isTestEnvironment()) return;

        var minecraftData = new JsonObject();
        minecraftData.addProperty("name", minecraftVersionName);
        minecraftData.addProperty("type", minecraftVersionType);
        minecraftData.addProperty("side", minecraftEnvironment.name().toLowerCase(Locale.ROOT));

        var hostData = new JsonObject();
        hostData.addProperty("os", System.getProperty("os.name"));
        hostData.addProperty("arch", System.getProperty("os.arch"));
        hostData.addProperty("version", System.getProperty("os.version"));

        JsonObject sendData = new JsonObject();
        sendData.addProperty("is_dev", isDevelopmentEnvironment || "development".equals(versionCommit));
        sendData.addProperty("is_ide", isDevelopmentEnvironment);
        sendData.addProperty("is_test", isTesting);
        sendData.addProperty("version_commit", versionCommit);
        sendData.addProperty("version", versionName);
        sendData.add("minecraft", minecraftData);
        sendData.add("host", hostData);
        sendData.addProperty("run_uuid", runUuid.toString());
        sendData.addProperty("timestamp", System.currentTimeMillis());
        sendData.addProperty("report_type", type);
        sendData.addProperty("report_version", "v11");
        sendData.add("data", data);

        reportQueue.add(sendData);
    }

    private static void writeExceptionJson(Throwable exception, JsonObject json) {
        if(!ENABLE_REPORTING) return;
        json.addProperty("exception_type", exception.getClass().getName());
        var trace = new StringBuilder();
        for(var element : exception.getStackTrace()) {
            trace.append(element).append("\n");
        }
        json.addProperty("exception_stack_trace", trace.toString());
        if(exception.getCause() != null) {
            JsonObject cause = new JsonObject();
            writeExceptionJson(exception.getCause(), cause);
            json.add("caused_by", cause);
        }
    }

    public static void reportException(String step, Throwable exception, JsonElement extra) {
        if(!ENABLE_REPORTING) return;
        JsonObject json = new JsonObject();
        json.addProperty("step", step);
        writeExceptionJson(exception, json);
        json.add("extra", extra);
        reportRawData("exception", json);
    }

    public static void reportExceptionWithInstructions(String step, Throwable exception, List<Instruction> instructions) {
        if(!ENABLE_REPORTING) return;
        JsonObject json = new JsonObject();
        // Datapack content is never sent; only its size.
        json.addProperty("instruction_count", instructions.size());
        reportException(step, exception, json);
    }

    public static void reportExceptionWithCommands(String step, Throwable exception, List<String> commands) {
        if(!ENABLE_REPORTING) return;
        JsonObject json = new JsonObject();
        // Datapack content is never sent; only its size.
        json.addProperty("command_count", commands.size());
        reportException(step, exception, json);
    }

    public static void reportCompilationStatistics(
            FunctionCountsData functionCounts,
            TimingData timings,
            CodeGenerationMetrics codeGenerationMetrics,
            Collection<Diagnostic> diagnostics
        ) {
        if(!ENABLE_REPORTING) return;
        JsonObject compilationStats = new JsonObject();

        JsonObject functionCountsJson = new JsonObject();
        functionCountsJson.addProperty("found", functionCounts.foundFunctionCount());
        functionCountsJson.addProperty("parsed", functionCounts.parsedFunctionCount());
        functionCountsJson.addProperty("compiled", functionCounts.compiledFunctionCount());
        functionCountsJson.addProperty("loaded", functionCounts.loadedFunctionCount());
        compilationStats.add("function_counts", functionCountsJson);

        JsonObject timingsJson = new JsonObject();
        timingsJson.addProperty("parse", timings.parse());
        timingsJson.addProperty("compile", timings.compile());
        timingsJson.addProperty("optimize", timings.optimize());
        timingsJson.addProperty("class_load", timings.classLoad());
        timingsJson.addProperty("handle_load", timings.handleLoad());
        compilationStats.add("timings", timingsJson);

        JsonObject codeGenerationMetricsJson = new JsonObject();
        codeGenerationMetricsJson.addProperty("total_command_count", codeGenerationMetrics.totalCommandCount());
        codeGenerationMetricsJson.addProperty("total_unoptimized_high_level_instruction_count", codeGenerationMetrics.totalUnoptimizedHighLevelInstructionCount());
        codeGenerationMetricsJson.addProperty("total_high_level_instruction_count", codeGenerationMetrics.totalHighLevelInstructionCount());
        codeGenerationMetricsJson.addProperty("total_low_level_instruction_count", codeGenerationMetrics.totalLowLevelInstructionCount());
        codeGenerationMetricsJson.addProperty("total_function_class_file_size", codeGenerationMetrics.totalFunctionClassFileSize());
        codeGenerationMetricsJson.addProperty("total_data_class_file_size", codeGenerationMetrics.totalDataClassFileSize());
        compilationStats.add("code_generation_metrics", codeGenerationMetricsJson);

        JsonArray diagnosticsJson = new JsonArray();
        for(var diagnostic : diagnostics) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", diagnostic.diagnosticId());
            obj.addProperty("severity", diagnostic.severity().toString().toLowerCase(Locale.ROOT));
            obj.addProperty("kind", diagnostic.kind().toString().toLowerCase(Locale.ROOT));
            if(diagnostic.cause() != null) {
                JsonObject exception = new JsonObject();
                writeExceptionJson(diagnostic.cause(), exception);
                obj.add("cause", exception);
            }
            diagnosticsJson.add(obj);
        }
        compilationStats.add("diagnostics", diagnosticsJson);

        reportRawData("compilation_stats", compilationStats);
    }

    public static void sendReports() {
        if(!ENABLE_REPORTING || dataReportingStatus != ReportingStatus.ENABLED || reportQueue.isEmpty()) return;
        var sendData = new JsonArray();
        for(JsonObject report; (report = reportQueue.poll()) != null; ) sendData.add(report);
        if(sendData.isEmpty()) return;

        SENDER.execute(() -> {
            try {
                var response = httpClient.send(
                        HttpRequest.newBuilder()
                                .uri(REPORT_URI)
                                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(sendData)))
                                .setHeader("Content-Type", "application/json")
                                .timeout(HTTP_TIMEOUT)
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if(response.statusCode() == 400) {
                    var respData = new Gson().fromJson(response.body(), JsonObject.class);
                    if(!respData.get("ok").getAsBoolean()) {
                        var errorId = respData.get("error_id");
                        if(Objects.equals(errorId.getAsString(), "UNSUPPORTED_VERSION")) {
                            dataReportingStatus = ReportingStatus.DISABLED_BY_UNSUPPORTED_VERSION;
                        }
                    }
                }
            }catch(Exception ignored) {}
        });
    }
}
