package dev.epicpix.minecraftfunctioncompiler.mod.test.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Replaces EntityLookup.add/remove at HEAD with an equivalent body and cancels, the way server mods with their own
 * (concurrent) entity lookup do (Fabrolia's EntityLookupMixin). Every test then runs with vanilla's method bodies
 * skipped, so the per-type entity index must not depend on code inside them.
 */
@Mixin(EntityLookup.class)
public class ReplacedEntityLookupMixin<T extends EntityAccess> {

    @Shadow @Final private Int2ObjectMap<T> byId;
    @Shadow @Final private Map<UUID, T> byUuid;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void mcfcTest$replacedAdd(T entity, CallbackInfo ci) {
        ci.cancel();
        if(byUuid.putIfAbsent(entity.getUUID(), entity) == null) byId.put(entity.getId(), entity);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void mcfcTest$replacedRemove(T entity, CallbackInfo ci) {
        ci.cancel();
        byUuid.remove(entity.getUUID());
        byId.remove(entity.getId());
    }
}
