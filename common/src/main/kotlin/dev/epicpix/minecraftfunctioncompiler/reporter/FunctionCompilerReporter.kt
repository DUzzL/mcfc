package dev.epicpix.minecraftfunctioncompiler.reporter

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.epicpix.minecraftfunctioncompiler.isDevelopmentEnvironment
import dev.epicpix.minecraftfunctioncompiler.versionCommit
import dev.epicpix.minecraftfunctioncompiler.versionName
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

object FunctionCompilerReporter {

    private val runUuid = UUID.randomUUID()
    private const val REPORT_URL = "https://mods.epicpix.dev/api/mcfc/v1/use_report"

    private var minecraftVersionName: String? = null
    private var minecraftVersionType: String? = null

    fun setMinecraftVersion(version: String, versionType: String) {
        if(minecraftVersionName != null) error("Tried to set minecraft version twice")
        minecraftVersionName = version
        minecraftVersionType = versionType
    }

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
        Thread {
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URL(REPORT_URL).toURI()).POST(BodyPublishers.ofString(Gson().toJson(sendData))).build(),
                BodyHandlers.ofString()
            )
        }.apply { name = "Function Compiler Data Reporter" }.start()
    }

}