package dev.epicpix.minecraftfunctioncompiler.mod;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Per-{@link EntityType} view of a vanilla {@code EntityLookup} (the "visible entities" of a level), implemented by
 * {@code mixin.EntityLookupTypeIndexMixin}. Each bucket holds exactly the entities of one type that are in the lookup's
 * {@code byId} map, in the order they entered it.
 */
public interface EntityTypeIndex {
    /**
     * The entities of {@code type}, or null when there are none. The array is a snapshot shared by every caller until
     * the next add/remove of that type: callers must not modify it.
     */
    Entity @Nullable [] mcfc$entitiesOfType(EntityType<?> type);

    /** One type's members plus the snapshot handed out to readers; guarded by the index lock. */
    final class Bucket {
        private static final Entity[] EMPTY = new Entity[0];

        private final ObjectLinkedOpenHashSet<Entity> members = new ObjectLinkedOpenHashSet<>();
        private Entity @Nullable [] snapshot = EMPTY;

        public void add(Entity entity) {
            if(members.add(entity)) snapshot = null;
        }

        public void remove(Entity entity) {
            if(members.remove(entity)) snapshot = null;
        }

        /** The members as an array, rebuilt only after a change; null when empty. */
        public Entity @Nullable [] snapshot() {
            var current = snapshot;
            if(current == null) {
                current = members.toArray(EMPTY);
                snapshot = current;
            }
            return current.length == 0 ? null : current;
        }
    }
}
