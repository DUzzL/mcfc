package dev.epicpix.minecraftfunctioncompiler.registry;

import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.EntityData;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;

public final class DataRegistries {
    private DataRegistries() {}

    public static final DataRegistry<AttributeData> ATTRIBUTE = new DataRegistry<>();
    public static final DataRegistry<EntityData> ENTITY_CLASS = new DataRegistry<>();
    public static final DataRegistry<MobEffectData> MOB_EFFECT = new DataRegistry<>();

    public static final RawDataRegistry<String, ScoreboardCriterionData> SCOREBOARD_CRITERIA = new RawDataRegistry<>();
}
