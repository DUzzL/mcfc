package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import dev.epicpix.minecraftfunctioncompiler.test.CompiledTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
            McfcTestHelper.loadTest(helper, compiledTest);

            var functions = helper.getLevel().getServer().getFunctions();
            var runFunction = functions.get(Identifier.fromNamespaceAndPath("mcfc-test", "run")).orElseThrow(() -> helper.assertionException(Component.literal("mcfc-test:run function not found")));

            // Anchor at the test structure: the framework guarantees only its chunks are entity-ticking,
            // while the server source sits at the respawn point whose chunk may be unloaded (entities
            // summoned there are invisible to @e, making both vanilla and compiled runs flaky).
            var cmdStack = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
            var mcfcData = FunctionCompilerMain.functionClassLoader;

            assert mcfcData != null;
            if(mcfcData.loadedFunctionCount() != mcfcData.totalFunctionCount()) {
                throw helper.assertionException(Component.literal("loaded function count (" + mcfcData.loadedFunctionCount() + ") != total function count (" + mcfcData.totalFunctionCount() + ")"));
            }

            mcfcData.tryOn();
            McfcTestHelper.clearServer(helper);
            McfcTestHelper.setupRun(helper, compiledTest);
            functions.execute(runFunction, cmdStack);
            var stateCompiled = McfcTestHelper.takeServerState(helper.getLevel().getServer());

            mcfcData.tryOff();
            McfcTestHelper.clearServer(helper);
            McfcTestHelper.setupRun(helper, compiledTest);
            functions.execute(runFunction, cmdStack);
            var stateVanilla = McfcTestHelper.takeServerState(helper.getLevel().getServer());

            if(!stateCompiled.equals(stateVanilla)) {
                throw helper.assertionException(Component.literal("vanilla execution results don't match compiled execution results: compiled " + stateCompiled.objectives() + ", vanilla " + stateVanilla.objectives()));
            }

            compiledTest.validate(stateVanilla, message -> {
                throw helper.assertionException(Component.literal(message));
            });

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
