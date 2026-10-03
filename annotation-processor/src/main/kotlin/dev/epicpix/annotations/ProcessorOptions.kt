package dev.epicpix.annotations

import dev.epicpix.annotations.tests.TestInfo
import java.io.File

data class ProcessorOptions(
    val dataDir: String,
    val jars: List<String>?,
    val project: String,
    val sourceSet: String,
    val minecraftVersion: MinecraftVersion?,
    val commit: String,
    val version: String,
    val tests: List<TestInfo>?,
) {
    companion object {
        fun fromMap(map: Map<String, String>): ProcessorOptions {
            return ProcessorOptions(
                dataDir = map["data_dir"]!!,
                jars = map["jars"]?.split(File.pathSeparator),
                project = map["project"]!!,
                sourceSet = map["source_set"]!!,
                minecraftVersion = map["minecraft_version"]?.let { MinecraftVersion.parse(it) },
                commit = map["commit"]!!,
                version = map["version"]!!,
                tests = map["tests"]?.let {
                    File(it).walkTopDown().toList()
                        .map { it.absolutePath }
                        .filter { it.endsWith(".mctest") }
                        .map {
                            TestInfo(
                                it.replaceFirst(map["tests"]!! + File.separator, "")
                                    .replaceFirst(".mctest", "")
                                    .replace(File.separatorChar, '/'),
                                File(it)
                            )
                        }
                        .sortedBy { it.id }
                },
            )
        }
    }
}
