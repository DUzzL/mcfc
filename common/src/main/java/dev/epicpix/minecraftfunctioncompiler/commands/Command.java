package dev.epicpix.minecraftfunctioncompiler.commands;

import java.util.Map;

public record Command(CommandType type, Map<String, ?> params) {
    @SuppressWarnings("unchecked")
    public <T> T get(String param) {
        return (T) params.get(param);
    }
}
