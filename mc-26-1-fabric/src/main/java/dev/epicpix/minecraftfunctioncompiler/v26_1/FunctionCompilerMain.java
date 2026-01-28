package dev.epicpix.minecraftfunctioncompiler.v26_1;

import dev.epicpix.minecraftfunctioncompiler.GlobalConfig;
import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.Emitter;
import dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.Emitter2;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public class FunctionCompilerMain implements ModInitializer {
    public static @Nullable FunctionClassLoader functionClassLoader;
    public FunctionCompilerMain() {
        GlobalConfig.loadAndSaveConfig(FabricLoader.getInstance().getConfigDir().resolve(GlobalConfig.DEFAULT_CONFIG_FILE));
    }
    public void onInitialize() {
        EmitterBaseOptions.registerInstructionEmitters();
        Emitter2.registerInstructionEmitters();
        Emitter.registerInstructionEmitters();
        SelectorParser.initBaseSelectorOptions();
    }
}
