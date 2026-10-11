package dev.epicpix.minecraftfunctioncompiler.mod;

import it.unimi.dsi.fastutil.objects.ObjectIterators;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.util.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Runtime side of compiled entity selectors. Everything reads vanilla's live structures on every call (no snapshot or
 * memo that could go stale within a tick), in vanilla's iteration order, so candidate membership and the order used by
 * {@code limit}/{@code execute as} match {@code EntitySelector.findEntities}. The compiled code applies the filters.
 */
@SuppressWarnings("unused") // called from generated code
public final class EntitySelection {
    private EntitySelection() {}

    /** Untyped {@code @e}: {@code ServerLevel.getAllEntities()}, i.e. EntityLookup.byId in insertion order. */
    public static Iterator<Entity> all(ServerLevel level) {
        return level.getAllEntities().iterator();
    }

    /** {@code @e[type=X]}: the entities of exactly type X from the level's EntityLookup, in the order they entered it. */
    public static Iterator<Entity> ofType(ServerLevel level, EntityType<?> type) {
        var entities = ((EntityTypeIndex) (Object) level.entityManager.visibleEntityStorage).mcfc$entitiesOfType(type);
        return entities == null ? Collections.emptyIterator() : ObjectIterators.wrap(entities);
    }

    /**
     * Entities whose bounding box intersects the box, plus intersecting ender dragon parts right after their dragon, in
     * the order of {@code Level.getEntities(EntityTypeTest, AABB, ...)}: section x ascending, then the section's packed
     * (z, y) key ascending (unsigned field order, so non-negative coordinates before negative ones), then insertion
     * order within the section.
     */
    public static List<Entity> inside(ServerLevel level, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        var result = new ArrayList<Entity>();
        var sectionStorage = level.entityManager.sectionStorage;
        int xMin = SectionPos.posToSectionCoord(minX - 2.0);
        int yMin = SectionPos.posToSectionCoord(minY - 4.0);
        int zMin = SectionPos.posToSectionCoord(minZ - 2.0);
        int xMax = SectionPos.posToSectionCoord(maxX + 2.0);
        int yMax = SectionPos.posToSectionCoord(maxY);
        int zMax = SectionPos.posToSectionCoord(maxZ + 2.0);
        // double: the long product overflows (goes negative) for boxes spanning ~2^21 sections per axis.
        double candidates = (double) (xMax - xMin + 1) * (yMax - yMin + 1) * (zMax - zMin + 1);
        if(candidates > 64 + 4.0 * sectionStorage.sectionIds.size()) {
            // Huge box: walking the existing sections (vanilla's own path, same order) beats probing every position.
            sectionStorage.getEntities(new AABB(minX, minY, minZ, maxX, maxY, maxZ), entity -> {
                addWithParts(result, entity, minX, minY, minZ, maxX, maxY, maxZ);
                return Continuation.CONTINUE;
            });
            return result;
        }
        for(int x = xMin; x <= xMax; x++) {
            // Unsigned field order: [max(min,0) .. max] then [min .. min(max,-1)].
            for(int zPass = 0; zPass < 2; zPass++) {
                int zFrom = zPass == 0 ? Math.max(zMin, 0) : zMin;
                int zTo = zPass == 0 ? zMax : Math.min(zMax, -1);
                for(int z = zFrom; z <= zTo; z++) {
                    for(int yPass = 0; yPass < 2; yPass++) {
                        int yFrom = yPass == 0 ? Math.max(yMin, 0) : yMin;
                        int yTo = yPass == 0 ? yMax : Math.min(yMax, -1);
                        for(int y = yFrom; y <= yTo; y++) {
                            var section = sectionStorage.getSection(SectionPos.asLong(x, y, z));
                            if(section == null || section.isEmpty() || !section.getStatus().isAccessible()) continue;
                            List<Entity> entities = section.storage.allInstances;
                            for(int i = 0, n = entities.size(); i < n; i++) {
                                var entity = entities.get(i);
                                if(entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                                    addWithParts(result, entity, minX, minY, minZ, maxX, maxY, maxZ);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void addWithParts(List<Entity> result, Entity entity, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        result.add(entity);
        if(entity instanceof EnderDragon dragon) {
            for(EnderDragonPart part : dragon.getSubEntities()) {
                if(part.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) result.add(part);
            }
        }
    }

    // ---- sort + limit (EntitySelector.sortAndLimit): stable, Doubles.compare on distanceToSqr ----

    public static void sortNearest(Vec3 origin, List<Entity> list, int limit) {
        sortByDistance(origin, list, limit, false);
    }

    public static void sortFurthest(Vec3 origin, List<Entity> list, int limit) {
        sortByDistance(origin, list, limit, true);
    }

    private static int compare(double a, double b, boolean furthest) {
        return furthest ? Double.compare(b, a) : Double.compare(a, b);
    }

    private static void sortByDistance(Vec3 origin, List<Entity> list, int limit, boolean furthest) {
        int n = list.size();
        if(n <= 1) return;
        double[] dist = new double[n];
        for(int i = 0; i < n; i++) dist[i] = list.get(i).distanceToSqr(origin);
        int keep = Math.min(limit, n);
        int[] order;
        if(keep <= 8 && keep < n) {
            // Partial selection of the `keep` best, ties by original index (what a stable sort would put first).
            order = new int[keep];
            int count = 0;
            for(int i = 0; i < n; i++) {
                double d = dist[i];
                if(count == keep && compare(d, dist[order[count - 1]], furthest) >= 0) continue;
                int pos = count == keep ? keep - 1 : count;
                while(pos > 0 && compare(d, dist[order[pos - 1]], furthest) < 0) {
                    order[pos] = order[pos - 1];
                    pos--;
                }
                order[pos] = i;
                if(count < keep) count++;
            }
        }else {
            order = new int[n];
            for(int i = 0; i < n; i++) order[i] = i;
            mergeSort(order, new int[n], 0, n, dist, furthest);
        }
        var sorted = new Entity[keep];
        for(int i = 0; i < keep; i++) sorted[i] = list.get(order[i]);
        for(int i = 0; i < keep; i++) list.set(i, sorted[i]);
        if(n > keep) list.subList(keep, n).clear();
    }

    private static void mergeSort(int[] a, int[] tmp, int from, int to, double[] dist, boolean furthest) {
        if(to - from < 2) return;
        if(to - from <= 16) {
            for(int i = from + 1; i < to; i++) {
                int v = a[i];
                int j = i;
                while(j > from && compare(dist[v], dist[a[j - 1]], furthest) < 0) {
                    a[j] = a[j - 1];
                    j--;
                }
                a[j] = v;
            }
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(a, tmp, from, mid, dist, furthest);
        mergeSort(a, tmp, mid, to, dist, furthest);
        if(compare(dist[a[mid - 1]], dist[a[mid]], furthest) <= 0) return;
        System.arraycopy(a, from, tmp, from, to - from);
        int i = from, j = mid, k = from;
        while(i < mid && j < to) a[k++] = compare(dist[tmp[j]], dist[tmp[i]], furthest) < 0 ? tmp[j++] : tmp[i++];
        while(i < mid) a[k++] = tmp[i++];
        while(j < to) a[k++] = tmp[j++];
    }

    /** {@code sort=random}: vanilla shuffles the whole list and keeps the first {@code limit}; a partial Fisher-Yates has the same distribution. */
    public static void shuffle(List<Entity> list, int limit) {
        int n = list.size();
        if(n <= 1) return;
        if(limit >= n) {
            Collections.shuffle(list);
            return;
        }
        var random = ThreadLocalRandom.current();
        for(int i = 0; i < limit; i++) {
            Collections.swap(list, i, i + random.nextInt(n - i));
        }
        list.subList(limit, n).clear();
    }

    /** x_rotation / y_rotation (EntitySelectorParser.createRotationPredicate); min and max are already wrapped. */
    public static boolean rotationInRange(float rotation, float min, float max) {
        float r = Mth.wrapDegrees(rotation);
        return min > max ? r >= min || r <= max : r >= min && r <= max;
    }

    // ---- execute on <relation>: ExecuteCommand.expandOneToOneEntityRelation drops removed entities ----

    private static <E extends Entity> @Nullable E present(@Nullable E entity) {
        return entity == null || entity.isRemoved() ? null : entity;
    }

    public static @Nullable Entity vehicle(Entity entity) {
        return present(entity.getVehicle());
    }

    public static @Nullable Entity controller(Entity entity) {
        return present(entity.getControllingPassenger());
    }

    public static @Nullable Entity leasher(Entity entity) {
        return entity instanceof net.minecraft.world.entity.Leashable leashable ? present(leashable.getLeashHolder()) : null;
    }

    public static @Nullable Entity attacker(Entity entity) {
        return entity instanceof net.minecraft.world.entity.Attackable attackable ? present(attackable.getLastAttacker()) : null;
    }

    public static @Nullable Entity owner(Entity entity) {
        return entity instanceof net.minecraft.world.entity.OwnableEntity ownable ? present(ownable.getOwner()) : null;
    }

    public static net.minecraft.world.entity.@Nullable LivingEntity target(net.minecraft.world.entity.Targeting targeting) {
        return present(targeting.getTarget());
    }

    public static @Nullable Entity origin(net.minecraft.world.entity.TraceableEntity traceable) {
        return present(traceable.getOwner());
    }

    /** {@code execute on passengers}: getPassengers() is an immutable snapshot; drop removed ones (normally none, so no copy). */
    public static List<Entity> passengers(Entity entity) {
        List<Entity> passengers = entity.getPassengers();
        for(int i = 0, n = passengers.size(); i < n; i++) {
            if(passengers.get(i).isRemoved()) {
                var alive = new ArrayList<Entity>(n);
                for(var p : passengers) if(!p.isRemoved()) alive.add(p);
                return alive;
            }
        }
        return passengers;
    }
}
