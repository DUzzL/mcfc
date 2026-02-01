package dev.epicpix.minecraftfunctioncompiler.test.state;

import dev.epicpix.minecraftfunctioncompiler.test.ExceptionThrow;

import java.util.HashMap;

public record ServerState(HashMap<String, ObjectiveState> objectives) {
    public ObjectiveState assertObjectiveExists(ExceptionThrow thrower, String name) {
        if(!objectives.containsKey(name)) thrower.accept(String.format("Objective '%s' does not exist when it was supposed to", name));
        return objectives.get(name);
    }

    public void assertObjectiveNotExists(ExceptionThrow thrower, String name) {
        if(objectives.containsKey(name)) thrower.accept(String.format("Objective '%s' exists when it was not supposed to", name));
    }
}
