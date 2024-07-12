package dev.epicpix.minecraftfunctioncompiler.reporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.CommandError;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationStatistics;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.isDevelopmentEnvironment;
import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.versionCommit;
import static dev.epicpix.minecraftfunctioncompiler.CompileConstants.versionName;
import static dev.epicpix.minecraftfunctioncompiler.il.Instruction.printInstructionList;

public final class FunctionCompilerReporter {
    private FunctionCompilerReporter() {}

    private static boolean dataReportingEnabled = true;

    private static final UUID runUuid = UUID.randomUUID();
    private static final String REPORT_URL = "https://mcfc.mods.epicpix.dev/api/v1/send_reports";

    private static String minecraftVersionName = null;
    private static String minecraftVersionType = null;

    public static void setMinecraftVersion(String version, String versionType) {
        if(minecraftVersionName != null) throw new RuntimeException("Tried to set minecraft version twice");
        MinecraftVersion.setCurrentVersion(MinecraftVersion.getByName(version));
        minecraftVersionName = version;
        minecraftVersionType = versionType;
    }

    private static final ArrayList<JsonObject> reportQueue = new ArrayList<>();

    @SuppressWarnings("ConstantValue")
    public static void reportRawData(String type, JsonElement data) {
        JsonObject sendData = new JsonObject();
        sendData.addProperty("is_dev", isDevelopmentEnvironment || versionCommit == null);
        sendData.addProperty("is_ide", isDevelopmentEnvironment);
        sendData.addProperty("version_commit", versionCommit);
        sendData.addProperty("version", versionName);
        sendData.addProperty("minecraft_version_name", minecraftVersionName);
        sendData.addProperty("minecraft_version_type", minecraftVersionType);
        sendData.addProperty("run_uuid", runUuid.toString());
        sendData.addProperty("timestamp", System.currentTimeMillis());
        sendData.addProperty("report_type", type);
        sendData.add("data", data);

        if(dataReportingEnabled) {
            reportQueue.add(sendData);
        }
    }

    private static void writeExceptionJson(Throwable exception, JsonObject json) {
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
        JsonObject json = new JsonObject();
        json.addProperty("step", step);
        writeExceptionJson(exception, json);
        json.add("extra", extra);
        reportRawData("exception", json);
    }

    public static void reportExceptionWithCommand(String step, Throwable exception, String command) {
        JsonObject json = new JsonObject();
        json.addProperty("command", command);
        reportException(step, exception, json);
    }

    public static void reportExceptionWithInstructions(String step, Throwable exception, List<Instruction> instructions) {
        JsonObject json = new JsonObject();
        var instructionResult = new StringBuilder();
        printInstructionList(instructions, (it) -> instructionResult.append(it).append("\n"));
        json.addProperty("instructions", instructionResult.toString());
        reportException(step, exception, json);
    }

    public static void reportCompilationStatistics(
            FunctionCountsData functionCounts,
            TimingData timings,
            List<List<OptimizationStatistics>> optimizationStats,
            List<CommandError> commandErrors
        ) {
        JsonObject compilationStats = new JsonObject();

        JsonObject functionCountsJson = new JsonObject();
        functionCountsJson.addProperty("found", functionCounts.foundFunctionCount());
        functionCountsJson.addProperty("parsed", functionCounts.parsedFunctionCount());
        functionCountsJson.addProperty("compiled", functionCounts.compiledFunctionCount());
        compilationStats.add("function_counts", functionCountsJson);

        JsonObject timingsJson = new JsonObject();
        timingsJson.addProperty("parse", timings.parse());
        timingsJson.addProperty("compile", timings.compile());
        timingsJson.addProperty("class_load", timings.classLoad());
        timingsJson.addProperty("handle_load", timings.handleLoad());
        compilationStats.add("timings", timingsJson);

        JsonArray optimizations = new JsonArray();
        for(var optimizationData : optimizationStats) {
            JsonArray arr = new JsonArray();
            for(var optimization : optimizationData) {
                JsonObject optimizationObj = new JsonObject();
                optimizationObj.addProperty("optimization", optimization.type());
                optimizationObj.addProperty("instruction_count", optimization.instructionCount());
                optimizationObj.addProperty("time_taken", optimization.timeTaken());
                arr.add(optimizationObj);
            }
            optimizations.add(arr);
        }
        compilationStats.add("optimizations", optimizations);

        JsonArray commandErrorsJson = new JsonArray();
        for(var error : commandErrors) {
            JsonObject obj = new JsonObject();
            obj.addProperty("command", error.command());
            obj.addProperty("error_message", error.errorMessage());
            commandErrorsJson.add(obj);
        }
        compilationStats.add("command_errors", commandErrorsJson);

        reportRawData("compilation_stats", compilationStats);
    }

    public static void sendReports() {
        if(!dataReportingEnabled || reportQueue.isEmpty()) return;
        var sendData = new JsonArray();
        reportQueue.forEach(sendData::add);
        reportQueue.clear();

        Thread t = new Thread(() -> {
            try {
                var response = HttpClient.newHttpClient().send(
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
                            dataReportingEnabled = false;
                        }
                    }
                }
            }catch(Exception ignored) {}
        });
        t.setName("Function Compiler Data Reporter");
        t.start();
    }
}
