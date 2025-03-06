package dev.epicpix.minecraftfunctioncompiler.reporter;

import com.google.gson.*;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

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

    private static ReportingStatus dataReportingStatus = ENABLE_REPORTING ? ReportingStatus.ENABLED : ReportingStatus.DISABLED_BY_COMPILE_CONSTANTS;

    public static void setReportingEnabled(boolean enable) {
        if(dataReportingStatus == ReportingStatus.ENABLED && !enable) {
            dataReportingStatus = ReportingStatus.DISABLED_BY_CONFIG;
        }else if(dataReportingStatus == ReportingStatus.DISABLED_BY_CONFIG && enable) {
            dataReportingStatus = ReportingStatus.ENABLED;
        }
    }

    private static final UUID runUuid = UUID.randomUUID();
    private static final String REPORT_URL = "https://mcfc.mods.epicpix.dev/api/v1/send_reports";
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static String minecraftVersionName = null;
    private static String minecraftVersionType = null;
    private static MinecraftEnvironment minecraftEnvironment = null;

    public static void setMinecraftVersion(String version, String versionType, MinecraftEnvironment environment) {
        if(minecraftVersionName != null) throw new RuntimeException("Tried to set minecraft version twice");
        MinecraftVersion.setCurrentVersion(MinecraftVersion.getByName(version));
        minecraftVersionName = version;
        minecraftVersionType = versionType;
        minecraftEnvironment = environment;
    }

    private static final ArrayList<JsonObject> reportQueue = new ArrayList<>();

    @SuppressWarnings("ConstantValue")
    public static void reportRawData(String type, JsonElement data) {
        if(!ENABLE_REPORTING || dataReportingStatus != ReportingStatus.ENABLED) return;

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
        sendData.addProperty("version_commit", versionCommit);
        sendData.addProperty("version", versionName);
        sendData.add("minecraft", minecraftData);
        sendData.add("host", hostData);
        sendData.addProperty("run_uuid", runUuid.toString());
        sendData.addProperty("timestamp", System.currentTimeMillis());
        sendData.addProperty("report_type", type);
        sendData.addProperty("report_version", "v9");
        sendData.add("data", data);

        reportQueue.add(sendData);
    }

    private static void writeExceptionJson(Throwable exception, JsonObject json) {
        if(!ENABLE_REPORTING) return;
        json.addProperty("exception_type", exception.getClass().getName());
        json.addProperty("exception_message", exception.getMessage());
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
        var instructionResult = new StringBuilder();
        Instruction.printInstructions(instructions, (it) -> instructionResult.append("┗━ ").append(it).append("\n"));
        json.addProperty("instructions", instructionResult.toString());
        reportException(step, exception, json);
    }

    public static void reportExceptionWithCommands(String step, Throwable exception, List<String> commands) {
        if(!ENABLE_REPORTING) return;
        JsonObject json = new JsonObject();
        var commandResult = new StringBuilder();
        for(var command : commands) commandResult.append(command).append("\n");
        json.addProperty("commands", commandResult.toString());
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
            obj.addProperty("message", diagnostic.message());

            if(diagnostic.extra() != null) obj.addProperty("extra", diagnostic.extra());
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
        reportQueue.forEach(sendData::add);
        reportQueue.clear();

        Thread t = new Thread(() -> {
            try {
                var response = httpClient.send(
                        HttpRequest.newBuilder()
                                .uri(new URL(REPORT_URL).toURI())
                                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(sendData)))
                                .setHeader("Content-Type", "application/json")
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
        t.setName("Function Compiler Data Reporter");
        t.start();
    }
}
