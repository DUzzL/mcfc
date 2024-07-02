package dev.epicpix.minecraftfunctioncompiler.registry;

import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;

public final class DataRegistries {
    private DataRegistries() {}

    public static final DataRegistry<AttributeData> ATTRIBUTE = new DataRegistry<>();
    public static final DataRegistry<Class<?>> ENTITY_CLASS = new DataRegistry<>();
}
