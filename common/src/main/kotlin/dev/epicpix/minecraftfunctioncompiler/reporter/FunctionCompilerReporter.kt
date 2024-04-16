package dev.epicpix.minecraftfunctioncompiler.reporter

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.printInstructionList
import dev.epicpix.minecraftfunctioncompiler.isDevelopmentEnvironment
import dev.epicpix.minecraftfunctioncompiler.optimizer.OptimizationStatistics
import dev.epicpix.minecraftfunctioncompiler.versionCommit
import dev.epicpix.minecraftfunctioncompiler.versionName
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

object FunctionCompilerReporter {
    private var dataReportingEnabled: Boolean = true

    private val runUuid = UUID.randomUUID()
    private const val REPORT_URL = "https://mods.epicpix.dev/api/mcfc/v1/send_reports"

    private var minecraftVersionName: String? = null
    private var minecraftVersionType: String? = null

    fun setMinecraftVersion(version: String, versionType: String) {
        if(minecraftVersionName != null) error("Tried to set minecraft version twice")
        minecraftVersionName = version
        minecraftVersionType = versionType
    }

    private val reportQueue: MutableList<JsonObject> = mutableListOf()

    @Suppress("SENSELESS_COMPARISON")
    fun reportRawData(type: String, data: JsonElement) {
        val sendData = JsonObject().apply {
            addProperty("is_dev", isDevelopmentEnvironment || versionCommit == null)
            addProperty("is_ide", isDevelopmentEnvironment)
            addProperty("version_commit", versionCommit)
            addProperty("version", versionName)
            addProperty("minecraft_version_name", minecraftVersionName)
            addProperty("minecraft_version_type", minecraftVersionType)
            addProperty("run_uuid", runUuid.toString())
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("report_type", type)
            add("data", data)
        }
        if(dataReportingEnabled) {
            reportQueue.add(sendData)
        }
    }

    private fun writeExceptionJson(exception: Throwable, json: JsonObject) {
        json.apply {
            addProperty("exception_type", exception.javaClass.name)
            addProperty("exception_message", exception.message)
            val stackTrace = exception.stackTrace
            val trace = StringBuilder()
            stackTrace.forEach { trace.append(it).append("\n") }
            addProperty("exception_stack_trace", trace.toString())
            if(exception.cause != null) {
                add("caused_by", JsonObject().apply { writeExceptionJson(exception.cause!!, this) })
            }
        }
    }

    fun reportException(step: String, exception: Throwable, extra: JsonElement) {
        reportRawData("exception", JsonObject().apply {
            addProperty("step", step)
            writeExceptionJson(exception, this)
            add("extra", extra)
        })
    }

    fun reportExceptionWithInstructions(step: String, exception: Throwable, instructions: List<Instruction>) {
        reportException(step, exception, JsonObject().apply {
            val instructionResult = StringBuilder()
            printInstructionList(instructions, { instructionResult.append(it).append("\n") })
            addProperty("instructions", instructionResult.toString())
        })
    }

    fun reportCompilationStatistics(
        foundFunctionCount: Int,
        parsedFunctionCount: Int,
        compiledFunctionCount: Int,
        parseTime: Double,
        compileTime: Double,
        classLoadTime: Double,
        handleLoadTime: Double,
        optimizationStats: List<List<OptimizationStatistics>>,
    ) {
        reportRawData("compilation_stats", JsonObject().apply {
            add("function_counts", JsonObject().apply {
                addProperty("found", foundFunctionCount)
                addProperty("parsed", parsedFunctionCount)
                addProperty("compiled", compiledFunctionCount)
            })
            add("timings", JsonObject().apply {
                addProperty("parse", parseTime)
                addProperty("compile", compileTime)
                addProperty("class_load", classLoadTime)
                addProperty("handle_load", handleLoadTime)
            })
            add("instruction_counts", JsonObject().apply {
                addProperty("pre_optimization", optimizationStats.sumOf { it.first().instructionCount })
                addProperty("post_optimization", optimizationStats.sumOf { it.last().instructionCount })
            })
            add("optimizations", JsonArray().apply {
                for(optimizationData in optimizationStats) {
                    add(JsonArray().apply {
                        for(optimization in optimizationData) {
                            add(JsonObject().apply {
                                addProperty("optimization", optimization.type)
                                addProperty("instruction_count", optimization.instructionCount)
                                addProperty("time_taken", optimization.timeTaken)
                            })
                        }
                    })
                }
            })
        })
    }

    fun sendReports() {
        if(!dataReportingEnabled || reportQueue.isEmpty()) return
        val sendData = JsonArray().apply {
            reportQueue.forEach { add(it) }
            reportQueue.clear()
        }
        Thread {
            runCatching {
                val response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder().uri(URL(REPORT_URL).toURI()).POST(BodyPublishers.ofString(Gson().toJson(sendData))).build(),
                    BodyHandlers.ofString()
                )
                if(response.statusCode() == 400) {
                    val respData = Gson().fromJson(response.body(), JsonObject::class.java)
                    if(!respData["ok"].asBoolean) {
                        val errorId = respData["error_id"]
                        if(errorId != null && errorId.asString == "UNSUPPORTED_VERSION") {
                            dataReportingEnabled = false
                        }
                    }
                }
            }
        }.apply { name = "Function Compiler Data Reporter" }.start()
    }

}