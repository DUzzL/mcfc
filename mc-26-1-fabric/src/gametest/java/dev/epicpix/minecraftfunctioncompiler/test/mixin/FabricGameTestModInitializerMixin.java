package dev.epicpix.minecraftfunctioncompiler.test.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.epicpix.minecraftfunctioncompiler.test.GameTestMethodProvider;
import net.fabricmc.fabric.impl.gametest.FabricGameTestModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryLoadTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
@Mixin(FabricGameTestModInitializer.class)
public class FabricGameTestModInitializerMixin {

    @Inject(method = "registerDynamicEntries", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/gametest/TestAnnotationLocator;getTestMethods()Ljava/util/List;"))
    private static void registerDynamicEntries(List<RegistryLoadTask<?>> loadTasks, CallbackInfo ci, @Local(name = "testInstances") Registry<GameTestInstance> testInstances, @Local(name = "testEnvironmentDefinitionRegistry") Registry<TestEnvironmentDefinition<?>> envReg) {
        for(var provided : GameTestMethodProvider.getTests()) {
            Registry.register(envReg, provided.key(), new TestEnvironmentDefinition.AllOf());
            Registry.register(testInstances, provided.key(), provided.instantiate(envReg));
        }
    }

}
