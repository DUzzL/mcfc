package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.api.ModInitializer;

public class FunctionCompilerMain implements ModInitializer {
    public void onInitialize() {
        switch(McVersion.getVersion()) {
            case MC_1_20_1 -> dev.epicpix.minecraftfunctioncompiler.v1_20_1.FunctionCompilerMain.INSTANCE.onInitialize();
            case MC_1_20_2 -> dev.epicpix.minecraftfunctioncompiler.v1_20_2.FunctionCompilerMain.INSTANCE.onInitialize();
            case MC_1_20_3, MC_1_20_4 -> dev.epicpix.minecraftfunctioncompiler.v1_20_4.FunctionCompilerMain.INSTANCE.onInitialize();
        }
    }
}
