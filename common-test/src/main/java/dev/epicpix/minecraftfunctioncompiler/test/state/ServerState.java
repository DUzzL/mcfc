package dev.epicpix.minecraftfunctioncompiler.test.state;

import dev.epicpix.minecraftfunctioncompiler.test.ExceptionThrow;

import java.util.HashMap;
import java.util.List;

/**
 * {@code diagnostics} are the compiler's messages for the loaded test functions (same in both runs);
 * {@code runtimeErrors} the compiled functions that threw and were switched to vanilla.
 */
public record ServerState(HashMap<String, ObjectiveState> objectives, List<String> diagnostics, List<String> runtimeErrors) {
    public ObjectiveState assertObjectiveExists(ExceptionThrow thrower, String name) {
        if(!objectives.containsKey(name)) thrower.accept(String.format("Objective '%s' does not exist when it was supposed to", name));
        return objectives.get(name);
    }

    public void assertObjectiveNotExists(ExceptionThrow thrower, String name) {
        if(objectives.containsKey(name)) thrower.accept(String.format("Objective '%s' exists when it was not supposed to", name));
    }

    public void assertNoDiagnostics(ExceptionThrow thrower) {
        if(!diagnostics.isEmpty()) thrower.accept(String.format("Expected every command to compile, but got %d diagnostic(s): %s", diagnostics.size(), diagnostics.getFirst()));
    }

    /** A throwing compiled function runs through vanilla afterwards, so equal states alone would hide it. */
    public void assertNoRuntimeErrors(ExceptionThrow thrower) {
        if(!runtimeErrors.isEmpty()) thrower.accept("Compiled function threw: " + runtimeErrors.getFirst());
    }
}
