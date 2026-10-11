package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.epicpix.minecraftfunctioncompiler.mod.EntityTypeIndex;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
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

/**
 * Mirrors {@code EntityLookup.byId} per entity type (see {@link EntityTypeIndex}). The hooks wrap the whole of
 * {@code add}/{@code remove} and afterwards re-check byId for the entity and for whatever byId held under its id
 * before the call: an entity is indexed exactly while byId maps its id to it. Wrapping, instead of injecting at
 * HEAD/TAIL, keeps the index right when another mod replaces the method body: Fabrolia cancels both methods at HEAD
 * with its concurrent implementation, so a TAIL injection never ran and the index stayed empty. PersistentEntitySectionManager
 * calls add/remove from startTracking/stopTracking, so section visibility changes, unloads and dimension changes are
 * covered.
 *
 * <p>Mutations and snapshots are serialized on the index map: server mods may add/remove entities of one level from
 * several threads at once (Fabrolia islands). Compiled selectors read it only on the server thread.
 */
@Mixin(EntityLookup.class)
public abstract class EntityLookupTypeIndexMixin<T extends EntityAccess> implements EntityTypeIndex {

    @Shadow @Final private Int2ObjectMap<T> byId;

    @Unique private final Reference2ObjectOpenHashMap<EntityType<?>, Bucket> mcfc$byType = new Reference2ObjectOpenHashMap<>();

    @WrapMethod(method = "add")
    private void mcfc$indexAdd(T entityAccess, Operation<Void> original) {
        T previous = this.byId.get(entityAccess.getId());
        original.call(entityAccess);
        mcfc$reconcile(previous, entityAccess);
    }

    @WrapMethod(method = "remove")
    private void mcfc$indexRemove(T entityAccess, Operation<Void> original) {
        T previous = this.byId.get(entityAccess.getId());
        original.call(entityAccess);
        mcfc$reconcile(previous, entityAccess);
    }

    @Unique
    private void mcfc$reconcile(@Nullable T previous, T entityAccess) {
        synchronized(this.mcfc$byType) {
            if(previous != null && previous != entityAccess) mcfc$reconcile(previous);
            mcfc$reconcile(entityAccess);
        }
    }

    @Unique
    private void mcfc$reconcile(T entityAccess) {
        if(!(entityAccess instanceof Entity entity)) return;
        var bucket = this.mcfc$byType.get(entity.getType());
        if(this.byId.get(entity.getId()) == entityAccess) {
            if(bucket == null) {
                bucket = new Bucket();
                this.mcfc$byType.put(entity.getType(), bucket);
            }
            bucket.add(entity);
        }else if(bucket != null) {
            bucket.remove(entity);
        }
    }

    @Override
    public Entity @Nullable [] mcfc$entitiesOfType(EntityType<?> type) {
        synchronized(this.mcfc$byType) {
            var bucket = this.mcfc$byType.get(type);
            return bucket == null ? null : bucket.snapshot();
        }
    }
}
