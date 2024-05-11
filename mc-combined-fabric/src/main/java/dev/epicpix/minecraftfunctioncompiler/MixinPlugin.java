package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.lib.accesswidener.AccessWidenerReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {
        try(var reader = getClass().getResourceAsStream("/minecraftfunctioncompiler.v" + McVersion.getVersion().getVersionName().replaceAll("\\.", "_") + ".accesswidener")) {
            new AccessWidenerReader(FabricLoaderImpl.INSTANCE.getAccessWidener()).read(reader.readAllBytes(), null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    public List<String> getMixins() {
        var envType = FabricLoader.getInstance().getEnvironmentType();
        var rawMixins = List.of("ServerFunctionManagerMixin", envType == EnvType.CLIENT ? "client.MinecraftMixin" : "server.DedicatedServerMixin");
        var convertedMixins = rawMixins.stream().map(x -> "v" + McVersion.getVersion().getVersionName().replaceAll("\\.", "_") + "." + x).toList();
        return convertedMixins;
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
