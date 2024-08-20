package dev.epicpix.minecraftfunctioncompiler.data;

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
    List<NegatableData<String>> tags,
    Boolean hasTeam,
    List<NegatableData<String>> teams,
    Map<String, IntRangeArgument> scores,
    List<NegatableData<Gamemode>> gamemodes,
    DoubleRangeArgument distance,
    IntRangeArgument level,
    SelectorSort sort,
    FloatRangeArgument yaw,
    FloatRangeArgument pitch
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
                null
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
                pitch
        );
    }

    public boolean selectPlayers() {
        return entityTypes != null
                && !entityTypes.negated()
                && entityTypes.data().size() == 1
                && entityTypes.data().get(0).equals(new DataLocation("minecraft", "player"));
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

}
