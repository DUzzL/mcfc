package dev.epicpix.minecraftfunctioncompiler.registry;

import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.CriteriaData;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;

public final class DataRegistries {
    private DataRegistries() {}

    public static final DataRegistry<AttributeData> ATTRIBUTE = new DataRegistry<>();
    public static final DataRegistry<Class<?>> ENTITY_CLASS = new DataRegistry<>();
    public static final DataRegistry<MobEffectData> MOB_EFFECT = new DataRegistry<>();

    public static final RawDataRegistry<String, CriteriaData> CRITERIA = new RawDataRegistry<>();
}
