package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import net.minecraft.commands.CommandResultCallback;

public class CompiledCommandResultStore implements CommandResultCallback {
    public void onResult(boolean success, int value) {
        if(FunctionCompilerMain.functionClassLoader != null && FunctionCompilerMain.functionClassLoader.isUseReady()) {
            FunctionCompilerMain.functionClassLoader.storeResult((success ? 0 : (1L << 32)) | Integer.toUnsignedLong(value));
        }
    }
}