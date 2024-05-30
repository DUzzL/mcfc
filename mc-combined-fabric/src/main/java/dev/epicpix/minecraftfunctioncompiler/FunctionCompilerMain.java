package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.api.ModInitializer;

public class FunctionCompilerMain implements ModInitializer {
    public void onInitialize() {
        switch(McVersion.getVersion()) {
            case MC_1_20, MC_1_20_1 -> new dev.epicpix.minecraftfunctioncompiler.v1_20_1.FunctionCompilerMain().onInitialize();
            case MC_1_20_2 -> new dev.epicpix.minecraftfunctioncompiler.v1_20_2.FunctionCompilerMain().onInitialize();
            case MC_1_20_3, MC_1_20_4 -> new dev.epicpix.minecraftfunctioncompiler.v1_20_4.FunctionCompilerMain().onInitialize();
        }
    }
}
