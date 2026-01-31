package dev.epicpix.annotations

import java.io.File

data class ProcessorOptions(
    val dataDir: String,
    val jars: List<String>?,
    val definitions: String,
    val sourceSet: String,
    val minecraftVersion: MinecraftVersion?,
    val isJarTask: Boolean,
    val commit: String,
    val version: String,
) {
    companion object {
        fun fromMap(map: Map<String, String>): ProcessorOptions {
            return ProcessorOptions(
                dataDir = map["data_dir"]!!,
                jars = map["jars"]?.split(File.pathSeparator),
                definitions = map["definitions"]!!,
                sourceSet = map["source_set"]!!,
                minecraftVersion = map["minecraft_version"]?.let { MinecraftVersion.parse(it) },
                isJarTask = map["isJarTask"]!! == "true",
                commit = map["commit"]!!,
                version = map["version"]!!,
            )
        }
    }
}
