package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectorGeneratorBuilderData {
    public boolean self = false;
    public NegatableData<List<DataLocation>> entityTypes = null;
    public boolean requirePlayer = false;
    public final ArrayList<NegatableData<String>> tags = new ArrayList<>();
    public Boolean hasTags = null;
    public List<NegatableData<String>> teams = new ArrayList<>();
    public Boolean hasTeam = null;
    public Integer limit = null;
    public final HashMap<String, IntRangeArgument> scores = new HashMap<>();
    public NegatableData<Gamemode> gamemode = null;
    public IntRangeArgument level = null;
    public FloatRangeArgument yaw = null;
    public FloatRangeArgument pitch = null;

    public SelectorArgument build() {
        return new SelectorArgument(
                null,
                null,
                self,
                limit,
                entityTypes != null ? new NegatableData<>(List.copyOf(entityTypes.data()), entityTypes.negated()) : null,
                requirePlayer,
                hasTags,
                List.copyOf(tags),
                hasTeam,
                List.copyOf(teams),
                Map.copyOf(scores),
                gamemode,
                level,
                yaw,
                pitch
        );
    }

}
