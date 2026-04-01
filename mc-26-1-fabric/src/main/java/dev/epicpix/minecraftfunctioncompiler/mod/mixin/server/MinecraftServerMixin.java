package dev.epicpix.minecraftfunctioncompiler.mod.mixin.server;

import dev.epicpix.minecraftfunctioncompiler.mod.FunctionDebugLogger;
import com.mojang.datafixers.DataFixer;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import dev.epicpix.minecraftfunctioncompiler.reporter.MinecraftEnvironment;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Shadow public abstract String getServerModName();
    @Shadow public abstract long getNextTickTime();
    @Shadow public abstract int getTickCount();

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/ReentrantBlockableEventLoop;<init>(Ljava/lang/String;Z)V", shift = At.Shift.AFTER))
    public void init(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes, CallbackInfo ci) {
        FunctionCompilerReporter.setMinecraftVersion(SharedConstants.getCurrentVersion().name(), getServerModName(), MinecraftEnvironment.SERVER);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    public void tickServer(CallbackInfo ci) {
        FunctionDebugLogger.tick(getTickCount());
    }

}
