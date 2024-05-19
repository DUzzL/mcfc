package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SelectorGeneratorData {
    public NegatableData<List<DataLocation>> entityTypes = null;
    public final ArrayList<NegatableData<String>> tags = new ArrayList<>();
    public Boolean hasTags = null;
    public NegatableData<String> team = null;
    public Boolean hasTeam = null;
    public Integer limit = null;
    public final HashMap<String, IntRangeArgument> scores = new HashMap<>();
    public NegatableData<Gamemode> gamemode = null;
    public FloatRangeArgument yaw = null;
    public FloatRangeArgument pitch = null;
}
