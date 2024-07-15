package dev.epicpix.minecraftfunctioncompiler.commands;

public record CommandError(String command, String errorMessage, Step step, Throwable cause) {
    public CommandError(String command, String errorMessage) {
        this(command, errorMessage, Step.PARSE, null);
    }
    public CommandError(String errorMessage) {
        this(null, errorMessage, Step.COMPILE, null);
    }
    public enum Step {
        PARSE, COMPILE,
    }
}
