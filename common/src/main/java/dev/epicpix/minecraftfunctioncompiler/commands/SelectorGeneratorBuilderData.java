package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.DoubleRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode;
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorAdvancementCheck;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorSort;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectorGeneratorBuilderData {
    // Parse-time state mirroring vanilla EntitySelectorParser (not part of the built selector).
    boolean includesEntities = true;
    boolean typeLimited = false;
    boolean typeLimitedInversely = false;
    boolean positiveTypeTag = false;
    boolean limitSet = false;
    boolean sortSet = false;
    public boolean self = false;
    public @Nullable NegatableData<List<DataLocation>> entityTypes = null;
    public boolean requireAlive = false;
    public boolean requirePlayer = false;
    public final ArrayList<NegatableData<String>> names = new ArrayList<>();
    public final ArrayList<NegatableData<MacroTemplate<String>>> tags = new ArrayList<>();
    public @Nullable Boolean hasTags = null;
    public final ArrayList<NegatableData<String>> teams = new ArrayList<>();
    public @Nullable Boolean hasTeam = null;
    public @Nullable Integer limit = null;
    public final HashMap<String, IntRangeArgument> scores = new HashMap<>();
    public boolean scoresSet = false;
    public final ArrayList<NegatableData<Gamemode>> gamemodes = new ArrayList<>();
    public @Nullable DoubleRangeArgument distance = null;
    public @Nullable IntRangeArgument level = null;
    public SelectorSort sort = SelectorSort.ARBITRARY;
    public @Nullable FloatRangeArgument yaw = null;
    public @Nullable FloatRangeArgument pitch = null;
    public boolean worldLimited = false;
    public @Nullable Double x = null;
    public @Nullable Double y = null;
    public @Nullable Double z = null;
    public @Nullable Double dx = null;
    public @Nullable Double dy = null;
    public @Nullable Double dz = null;
    public @Nullable HashMap<DataLocation, SelectorAdvancementCheck> advancements = null;
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
