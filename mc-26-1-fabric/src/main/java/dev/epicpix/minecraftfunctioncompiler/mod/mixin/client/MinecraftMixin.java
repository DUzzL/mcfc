package dev.epicpix.minecraftfunctioncompiler.mod.mixin.client;

import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;launchedVersion:Ljava/lang/String;", shift = At.Shift.AFTER, opcode = Opcodes.PUTFIELD))
    public void init(GameConfig gameConfig, CallbackInfo ci) {
        MinecraftVersion.setCurrentVersion(MinecraftVersion.getByName(SharedConstants.getCurrentVersion().name()));
    }

}
