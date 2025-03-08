package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectorGeneratorBuilderData {
    public boolean self = false;
    public NegatableData<List<DataLocation>> entityTypes = null;
    public boolean requireAlive = false;
    public boolean requirePlayer = false;
    public final ArrayList<NegatableData<String>> names = new ArrayList<>();
    public final ArrayList<NegatableData<MacroTemplate<String>>> tags = new ArrayList<>();
    public Boolean hasTags = null;
    public final ArrayList<NegatableData<String>> teams = new ArrayList<>();
    public Boolean hasTeam = null;
    public Integer limit = null;
    public final HashMap<String, IntRangeArgument> scores = new HashMap<>();
    public final ArrayList<NegatableData<Gamemode>> gamemodes = new ArrayList<>();
    public DoubleRangeArgument distance = null;
    public IntRangeArgument level = null;
    public SelectorSort sort = SelectorSort.ARBITRARY;
    public FloatRangeArgument yaw = null;
    public FloatRangeArgument pitch = null;
    public boolean worldLimited = false;
    public Double x = null;
    public Double y = null;
    public Double z = null;
    public Double dx = null;
    public Double dy = null;
    public Double dz = null;
    public HashMap<DataLocation, SelectorAdvancementCheck> advancements = null;
    public final ArrayList<NegatableData<DataLocation>> predicates = new ArrayList<>();
    public final ArrayList<NegatableData<NbtValue.Compound>> nbts = new ArrayList<>();

    public SelectorArgument build() {
        return new SelectorArgument(
                null,
                null,
                self,
                limit,
                entityTypes != null ? new NegatableData<>(List.copyOf(entityTypes.data()), entityTypes.negated()) : null,
                requireAlive,
                requirePlayer,
                List.copyOf(names),
                hasTags,
                List.copyOf(tags),
                hasTeam,
                List.copyOf(teams),
                Map.copyOf(scores),
                List.copyOf(gamemodes),
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
            advancements != null ? Map.copyOf(advancements) : null,
            List.copyOf(predicates),
            List.copyOf(nbts)
        );
    }

}
