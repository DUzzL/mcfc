package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.mod.EntityTypeIndex;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors {@code EntityLookup.byId} per entity type (see {@link EntityTypeIndex}). The hooks follow byId exactly:
 * an entity enters its type set when {@code add} actually stored it (duplicate UUIDs are rejected by vanilla and so
 * never indexed) and leaves when {@code remove} drops whatever byId holds under its id. PersistentEntitySectionManager
 * calls add/remove from startTracking/stopTracking, so section visibility changes, unloads and dimension changes are
 * covered, and the per-type order is byId's insertion order.
 */
@Mixin(EntityLookup.class)
public abstract class EntityLookupTypeIndexMixin<T extends EntityAccess> implements EntityTypeIndex {

    @Shadow @Final private Int2ObjectMap<T> byId;

    @Unique private final Reference2ObjectOpenHashMap<EntityType<?>, ObjectLinkedOpenHashSet<Entity>> mcfc$byType = new Reference2ObjectOpenHashMap<>();

    @Inject(method = "add", at = @At("TAIL"))
    private void mcfc$indexAdd(T entityAccess, CallbackInfo ci) {
        if(entityAccess instanceof Entity entity && this.byId.get(entity.getId()) == entityAccess) {
            var set = this.mcfc$byType.get(entity.getType());
            if(set == null) {
                set = new ObjectLinkedOpenHashSet<>();
                this.mcfc$byType.put(entity.getType(), set);
            }
            set.add(entity);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void mcfc$indexRemove(T entityAccess, CallbackInfo ci) {
        if(this.byId.get(entityAccess.getId()) instanceof Entity current) {
            var set = this.mcfc$byType.get(current.getType());
            if(set != null) set.remove(current);
        }
    }

    @Override
    public @Nullable ObjectLinkedOpenHashSet<Entity> mcfc$entitiesOfType(EntityType<?> type) {
        return this.mcfc$byType.get(type);
    }
}
