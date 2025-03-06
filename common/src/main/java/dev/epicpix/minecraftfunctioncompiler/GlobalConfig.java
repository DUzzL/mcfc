package dev.epicpix.minecraftfunctioncompiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ENABLE_REPORTING;

public final class GlobalConfig {
    private GlobalConfig() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_CONFIG_FILE = "mcfc.json";

    private static final int CONFIG_VERSION = 1;


    public static boolean enableReporting = ENABLE_REPORTING;

    public static void loadAndSaveConfig(Path path) {
        var loadResult = loadConfig(path);
        if(loadResult != ConfigLoadResult.LOADED_NEWEST) {
            LOGGER.info(loadResult == ConfigLoadResult.NOT_LOADED ? "Creating a new config file." : "Updating the config file to latest version.");
            saveConfig(path);
        }
    }

    public enum ConfigLoadResult {
        LOADED_NEWEST,
        LOADED_OLD,
        NOT_LOADED,
    }

    public static void resetConfig() {
        enableReporting = ENABLE_REPORTING;
        FunctionCompilerReporter.setReportingEnabled(enableReporting);
    }

    public static ConfigLoadResult loadConfig(Path path) {
        if(Files.exists(path)) {
            try {
                var content = GSON.fromJson(Files.readString(path), JsonObject.class);
                var version = content.get("version").getAsInt();
                if(version == CONFIG_VERSION) {
                    enableReporting = getOrDefault(content, "enable_reporting", ENABLE_REPORTING);
                    if(enableReporting) {
                        if(!ENABLE_REPORTING) {
                            LOGGER.warn("This version was not compiled with telemetry reporting, \"enable_reporting\" config option ignored");
                            enableReporting = false;
                        }
                    }
                    FunctionCompilerReporter.setReportingEnabled(enableReporting);
                    return ConfigLoadResult.LOADED_NEWEST;
                }else {
                    throw new IllegalArgumentException("Invalid config version: " + version);
                }
            } catch(Exception e) {
                LOGGER.error("Failed to load config", e);
            }
        }
        return ConfigLoadResult.NOT_LOADED;
    }

    public static void saveConfig(Path path) {
        var content = new JsonObject();
        content.addProperty("_note", "To use new config options, first run the new version (for a proper config update), and then edit the config.");
        content.addProperty("version", CONFIG_VERSION);
        content.addProperty("enable_reporting", enableReporting);
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
