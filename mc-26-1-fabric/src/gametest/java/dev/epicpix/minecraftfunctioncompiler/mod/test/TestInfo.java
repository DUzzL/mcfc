package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.test.CompiledTest;
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

public record TestInfo(CompiledTest compiledTest) {

    private TestData<Holder<TestEnvironmentDefinition<?>>> testData(Registry<TestEnvironmentDefinition<?>> envReg) {
        return new TestData<>(
            envReg.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, identifier())),
            Identifier.withDefaultNamespace("empty"),
            1,
            1,
            true
        );
    }

    public Consumer<GameTestHelper> runner() {
        return helper -> {
            McfcTestHelper.setupRun(helper, compiledTest);
            var functions = helper.getLevel().getServer().getFunctions();
            functions.execute(functions.get(Identifier.fromNamespaceAndPath("mcfc-test", "run")).orElseThrow(), helper.getLevel().getServer().createCommandSourceStack());
            helper.succeed();
        };
    }

    public GameTestInstance instantiate(Registry<TestEnvironmentDefinition<?>> envReg) {
        return new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, identifier()), testData(envReg));
    }

    public Identifier identifier() {
        return Identifier.fromNamespaceAndPath(compiledTest.id().namespace(), compiledTest.id().path());
    }
}
