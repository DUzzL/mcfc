package dev.epicpix.minecraftfunctioncompiler.v1_20_1;

import dev.epicpix.minecraftfunctioncompiler.commands.SelectorParser;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.EmitterBaseOptions;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.Emitter;
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.Emitter2;
import net.fabricmc.api.ModInitializer;

public class FunctionCompilerMain implements ModInitializer {
    public static FunctionClassLoader functionClassLoader;
    public void onInitialize() {
        EmitterBaseOptions.registerInstructionEmitters();
        Emitter2.registerInstructionEmitters();
        Emitter.registerInstructionEmitters();
        SelectorParser.initBaseSelectorOptions();
    }
}
