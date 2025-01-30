package dev.epicpix.minecraftfunctioncompiler.data;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SelectorArgument(
    UUID entityUuid,
    String playerName,
    boolean self,
    Integer limit,
    NegatableData<List<DataLocation>> entityTypes,
    boolean requireAlive,
    boolean requirePlayer,
    List<NegatableData<String>> names,
    Boolean hasTags,
    List<NegatableData<MacroTemplate<String>>> tags,
    Boolean hasTeam,
    List<NegatableData<String>> teams,
    Map<String, IntRangeArgument> scores,
    List<NegatableData<Gamemode>> gamemodes,
    DoubleRangeArgument distance,
    IntRangeArgument level,
    SelectorSort sort,
    FloatRangeArgument yaw,
    FloatRangeArgument pitch,
    boolean worldLimited,
    Double x,
    Double y,
    Double z,
    Double dx,
    Double dy,
    Double dz,
    Map<DataLocation, SelectorAdvancementCheck> advancements,
    List<NegatableData<DataLocation>> predicates
) {

    private SelectorArgument(UUID entityUuid, String playerName, boolean self) {
        this(
                entityUuid,
                playerName,
                self,
                null,
                null,
                false,
                false,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                Map.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    public static SelectorArgument ofUuid(UUID uuid) {
        return new SelectorArgument(uuid, null, false);
    }

    public static SelectorArgument ofPlayer(String playerName) {
        return new SelectorArgument(null, playerName, false);
    }

    public static SelectorArgument ofSelf() {
        return new SelectorArgument(null, null, true);
    }

    public SelectorArgument limit(int limit) {
        return new SelectorArgument(
                entityUuid,
                playerName,
                self,
                limit,
                entityTypes,
                requireAlive,
                requirePlayer,
                names,
                hasTags,
                tags,
                hasTeam,
                teams,
                scores,
                gamemodes,
                distance,
                level,
                sort,
                yaw,
                pitch,
                worldLimited,
                x,
                y,
                z,
                dx,
                dy,
                dz,
                advancements,
                predicates
        );
    }

    public boolean excludesPlayers() {
        if(entityTypes != null) {
            if(entityTypes.negated()) {
                for(var type : entityTypes.data()) {
                    if(type.equals(new DataLocation("minecraft", "player"))) {
                        return true;
                    }
                }
            }else {
                if(entityTypes.data().isEmpty()) {
                    return false;
                }
                for(var type : entityTypes.data()) {
                    if(type.equals(new DataLocation("minecraft", "player"))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public AABB createLocalAABB() {
        double vdx = dx == null ? 0.0 : dx;
        double vdy = dy == null ? 0.0 : dy;
        double vdz = dz == null ? 0.0 : dz;

        double lx = Math.min(vdx, 0.0);
        double ly = Math.min(vdy, 0.0);
        double lz = Math.min(vdz, 0.0);
        double hx = Math.max(vdx, 0.0) + 1.0;
        double hy = Math.max(vdy, 0.0) + 1.0;
        double hz = Math.max(vdz, 0.0) + 1.0;

        double minX = Math.min(lx, hx);
        double minY = Math.min(ly, hy);
        double minZ = Math.min(lz, hz);
        double maxX = Math.max(lx, hx);
        double maxY = Math.max(ly, hy);
        double maxZ = Math.max(lz, hz);

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

}
