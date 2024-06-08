package dev.epicpix.minecraftfunctioncompiler.commands;

public class CommandErrorException extends RuntimeException {
    public final CommandError error;
    public CommandErrorException(CommandError error) {
        super(error.errorMessage());
        this.error = error;
    }
}
