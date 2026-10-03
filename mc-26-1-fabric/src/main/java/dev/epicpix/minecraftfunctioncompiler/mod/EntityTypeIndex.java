package dev.epicpix.minecraftfunctioncompiler.mod;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Per-{@link EntityType} view of a vanilla {@code EntityLookup} (the "visible entities" of a level), implemented by
 * {@code mixin.EntityLookupTypeIndexMixin}. Each set holds exactly the entities of one type that are in the lookup's
 * {@code byId} map, in the same relative (insertion) order, so iterating it equals vanilla's
 * {@code level.getEntities(type, predicate, ...)} scan restricted to that type. Server-thread only, like the lookup.
 */
public interface EntityTypeIndex {
    @Nullable ObjectLinkedOpenHashSet<Entity> mcfc$entitiesOfType(EntityType<?> type);
}
