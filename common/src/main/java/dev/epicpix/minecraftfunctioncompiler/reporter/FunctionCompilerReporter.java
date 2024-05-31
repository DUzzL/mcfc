package dev.epicpix.minecraftfunctioncompiler.reporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.optimizer.OptimizationStatistics;

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
    private static final String REPORT_URL = "https://mods.epicpix.dev/api/mcfc/v1/send_reports";

    private static String minecraftVersionName = null;
    private static String minecraftVersionType = null;

    public static void setMinecraftVersion(String version, String versionType) {
        if(minecraftVersionName != null) throw new RuntimeException("Tried to set minecraft version twice");
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

    public static void reportExceptionWithInstructions(String step, Throwable exception, List<Instruction> instructions) {
        JsonObject json = new JsonObject();
        var instructionResult = new StringBuilder();
        printInstructionList(instructions, (it) -> instructionResult.append(it).append("\n"), 0);
        json.addProperty("instructions", instructionResult.toString());
        reportException(step, exception, json);
    }

    public static void reportCompilationStatistics(
            int foundFunctionCount,
            int parsedFunctionCount,
            int compiledFunctionCount,
            double parseTime,
            double compileTime,
            double classLoadTime,
            double handleLoadTime,
            List<List<OptimizationStatistics>> optimizationStats
        ) {
        JsonObject compilationStats = new JsonObject();

        JsonObject functionCounts = new JsonObject();
        functionCounts.addProperty("found", foundFunctionCount);
        functionCounts.addProperty("parsed", parsedFunctionCount);
        functionCounts.addProperty("compiled", compiledFunctionCount);
        compilationStats.add("function_counts", functionCounts);

        JsonObject timings = new JsonObject();
        timings.addProperty("parse", parseTime);
        timings.addProperty("compile", compileTime);
        timings.addProperty("class_load", classLoadTime);
        timings.addProperty("handle_load", handleLoadTime);
        compilationStats.add("timings", timings);

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
                        HttpRequest.newBuilder().uri(new URL(REPORT_URL).toURI()).POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(sendData))).build(),
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
