package dev.epicpix.minecraftfunctioncompiler.test;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Consumer;

public record TestInfo(Identifier key, int maxTicks) {

    private TestData<Holder<TestEnvironmentDefinition<?>>> testData(Registry<TestEnvironmentDefinition<?>> envReg) {
        return new TestData<>(
            envReg.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, key)),
            Identifier.withDefaultNamespace("empty"),
            1,
            1,
            true
        );
    }

    public Consumer<GameTestHelper> runner() {
        return helper -> {
            System.out.println("Run " + helper.getLevel().getGameTime() + " " + key);
            helper.succeed();
        };
    }

    public GameTestInstance instantiate(Registry<TestEnvironmentDefinition<?>> envReg) {
        return new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, key), testData(envReg));
    }
}
