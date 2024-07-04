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
    Boolean hasTags,
    List<NegatableData<String>> tags,
    Boolean hasTeam,
    List<NegatableData<String>> teams,
    Map<String, IntRangeArgument> scores,
    NegatableData<Gamemode> gamemode,
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
                null,
                List.of(),
                null,
                null,
                Map.of(),
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
                hasTags,
                tags,
                hasTeam,
                teams,
                scores,
                gamemode,
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

}
