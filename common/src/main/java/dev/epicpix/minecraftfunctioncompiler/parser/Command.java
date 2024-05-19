package dev.epicpix.minecraftfunctioncompiler.parser;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandType;

import java.util.Map;

public record Command(CommandType type, Map<String, ?> params) {
    @SuppressWarnings("unchecked")
    public <T> T get(String param) {
        return (T) params.get(param);
    }
}
