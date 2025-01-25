package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public class CompiledCommandResultStore implements ResultConsumer<CommandSourceStack> {
    public void onCommandComplete(CommandContext<CommandSourceStack> context, boolean success, int value) {
        if(FunctionCompilerMain.functionClassLoader != null && FunctionCompilerMain.functionClassLoader.isUseReady()) {
            FunctionCompilerMain.functionClassLoader.storeResult((success ? 0 : (1L << 32)) | Integer.toUnsignedLong(value));
        }
    }
}
