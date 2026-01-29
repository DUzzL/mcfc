package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class FunctionCompilerTestMain implements ModInitializer {
    @Override
    public void onInitialize() {
        FunctionCompilerReporter.markTestEnvironment();

        for(var provided : GameTestMethodProvider.getTests()) {
            Registry.register(BuiltInRegistries.TEST_FUNCTION, provided.key(), provided.runner());
        }

    }
}
