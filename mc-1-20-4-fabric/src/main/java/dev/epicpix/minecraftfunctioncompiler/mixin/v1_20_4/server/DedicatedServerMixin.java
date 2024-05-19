package dev.epicpix.minecraftfunctioncompiler.mixin.v1_20_4.server;

import com.mojang.datafixers.DataFixer;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import net.minecraft.DetectedVersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin extends MinecraftServer {

    public DedicatedServerMixin(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory) {
        super(thread, levelStorageAccess, packRepository, worldStem, proxy, dataFixer, services, chunkProgressListenerFactory);
    }

    @Inject(method = "initServer", at = @At("HEAD"))
    public void initServer(CallbackInfoReturnable<Boolean> cir) {
        FunctionCompilerReporter.setMinecraftVersion(DetectedVersion.BUILT_IN.getName(), getServerModName());
    }

}
