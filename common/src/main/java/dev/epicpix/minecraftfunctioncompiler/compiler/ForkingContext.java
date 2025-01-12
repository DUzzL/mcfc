package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.List;

public class ForkingContext {

    private final LabelData forkExecLabel;
    private final LocationData<List<?>> forkContexts;

    public ForkingContext(LabelData forkExecLabel, LocationData<List<?>> forkContexts) {
        this.forkExecLabel = forkExecLabel;
        this.forkContexts = forkContexts;
    }

    public LabelData getForkExecLabel() {
        return forkExecLabel;
    }

    public LocationData<List<?>> getForkContextList() {
        return forkContexts;
    }

}
