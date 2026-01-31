package dev.epicpix.minecraftfunctioncompiler.mod.test.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.epicpix.minecraftfunctioncompiler.mod.test.TestPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(method = "discoverAvailable", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"))
    public void discoverAvailable(CallbackInfoReturnable<Map<String, Pack>> cir, @Local(name = "discovered")  Map<String, Pack> discovered) {
        discovered.put("mcfc-test", new TestPack());
    }

}
