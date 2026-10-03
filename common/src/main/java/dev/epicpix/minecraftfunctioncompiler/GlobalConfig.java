package dev.epicpix.minecraftfunctioncompiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ENABLE_REPORTING;

public final class GlobalConfig {
    private GlobalConfig() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_CONFIG_FILE = "mcfc.json";

    private static final int CONFIG_VERSION = 2;

    public static boolean enableReporting = ENABLE_REPORTING;
    public static boolean profilerCollectMemoryUsage = true;
    /**
     * Parallel datapack execution is not supported: it is hard-disabled regardless of the config file
     * (a set "enable_parallel_datapacks" key only produces a warning).
     */
    public static final boolean enableParallelDatapacks = false;

    public static void loadAndSaveConfig(Path path) {
        var loadResult = loadConfig(path);
        if(loadResult != ConfigLoadResult.LOADED_NEWEST) {
            LOGGER.info(loadResult == ConfigLoadResult.NOT_LOADED ? "Creating a new config file." : "Updating the config file to latest version.");
            saveConfig(path);
        }
    }

    public enum ConfigLoadResult {
        /** Current version with every key present. */
        LOADED_NEWEST,
        /** Loaded, but written by an older version or missing keys; should be re-saved. */
        LOADED_OLD,
        /** No usable file (missing, or unreadable and moved aside); defaults are in effect. */
        NOT_LOADED,
    }

    public static void resetConfig() {
        enableReporting = ENABLE_REPORTING;
        profilerCollectMemoryUsage = true;
        FunctionCompilerReporter.setReportingEnabled(enableReporting);
    }

    public static ConfigLoadResult loadConfig(Path path) {
        if(!Files.exists(path)) return ConfigLoadResult.NOT_LOADED;
        // Parse everything into locals first so a malformed file leaves the current settings untouched.
        boolean reporting;
        boolean memoryUsage;
        boolean complete;
        int version;
        try {
            var content = GSON.fromJson(Files.readString(path), JsonObject.class);
            if(content == null || !content.has("version")) throw new IllegalArgumentException("Missing \"version\"");
            version = content.get("version").getAsInt();
            if(version != 1 && version != CONFIG_VERSION) throw new IllegalArgumentException("Invalid config version: " + version);
            reporting = getOrDefault(content, "enable_reporting", ENABLE_REPORTING);
            memoryUsage = version == 1 || getOrDefault(content, "profiler_collect_memory_usage", true);
            complete = content.has("enable_reporting") && content.has("profiler_collect_memory_usage");
            if(getOrDefault(content, "enable_parallel_datapacks", false)) {
                LOGGER.warn("\"enable_parallel_datapacks\" is not supported and is ignored; functions always run serially");
            }
        } catch(Exception e) {
            var backup = path.resolveSibling(path.getFileName() + ".bak");
            LOGGER.error("Failed to load config {}, moving it to {} and using defaults", path, backup, e);
            try {
                Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch(Exception moveError) {
                LOGGER.error("Failed to move unreadable config {} aside", path, moveError);
            }
            return ConfigLoadResult.NOT_LOADED;
        }

        if(reporting && !ENABLE_REPORTING) {
            LOGGER.warn("This version was not compiled with telemetry reporting, \"enable_reporting\" config option ignored");
            reporting = false;
        }
        enableReporting = reporting;
        profilerCollectMemoryUsage = memoryUsage;
        FunctionCompilerReporter.setReportingEnabled(enableReporting);
        return version == CONFIG_VERSION && complete ? ConfigLoadResult.LOADED_NEWEST : ConfigLoadResult.LOADED_OLD;
    }

    public static void saveConfig(Path path) {
        var content = new JsonObject();
        content.addProperty("_note", "To use new config options, first run the new version (for a proper config update), and then edit the config.");
        content.addProperty("version", CONFIG_VERSION);
        content.addProperty("enable_reporting", enableReporting);
        content.addProperty("profiler_collect_memory_usage", profilerCollectMemoryUsage);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(content));
        }catch(Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }


    private static boolean getOrDefault(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

}
