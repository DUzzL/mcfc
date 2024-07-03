package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_1.client;

import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import net.minecraft.DetectedVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow @Final private String versionType;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;versionType:Ljava/lang/String;", shift = At.Shift.AFTER))
    public void init(GameConfig gameConfig, CallbackInfo ci) {
        FunctionCompilerReporter.setMinecraftVersion(DetectedVersion.BUILT_IN.getName(), versionType);
    }

}
