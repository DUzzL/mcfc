package dev.epicpix.minecraftfunctioncompiler.test.state;

import dev.epicpix.minecraftfunctioncompiler.test.ExceptionThrow;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public record ObjectiveState(String name, Object2IntOpenHashMap<String> values) {
    public void assertScoreExists(ExceptionThrow thrower, String holder) {
        if(!values.containsKey(holder)) thrower.accept(String.format("Objective '%s' does not have score '%s' when it was supposed to", name, holder));
    }

    public void assertScoreNotExists(ExceptionThrow thrower, String holder) {
        if(!values.containsKey(holder)) thrower.accept(String.format("Objective '%s' has score '%s' when it was not supposed to", name, holder));
    }

    public void assertScoreSet(ExceptionThrow thrower, String holder, int value) {
        if(!values.containsKey(holder)) thrower.accept(String.format("Objective '%s' has score '%s' set to nothing, when it was supposed to be %s", name, holder, value));
        else if(values.getInt(holder) != value) thrower.accept(String.format("Objective '%s' has score '%s' set to %s when it was supposed to be %s", name, holder, values.getInt(holder), value));
    }
}
