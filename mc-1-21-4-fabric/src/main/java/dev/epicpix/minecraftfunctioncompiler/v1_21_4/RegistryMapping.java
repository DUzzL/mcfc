package dev.epicpix.minecraftfunctioncompiler.v1_21_4;

import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

public final class RegistryMapping {
    private RegistryMapping() {}

    @SuppressWarnings("unchecked")
    public static void updateRegistryMaps(MinecraftServer server, ServerFunctionManager serverFunctionManager) throws IllegalAccessException {
        DataRegistries.ATTRIBUTE.reset();
        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(e -> {
            DataLocation location = new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath());
            if(e.getValue() instanceof RangedAttribute ranged) {
                DataRegistries.ATTRIBUTE.set(location, new AttributeData(location, e.getValue().getDefaultValue(), ranged.getMinValue(), ranged.getMaxValue()));
            }else {
                DataRegistries.ATTRIBUTE.set(location, new AttributeData(location, e.getValue().getDefaultValue(), Double.MIN_VALUE, Double.MAX_VALUE));
            }
        });
        DataRegistries.ATTRIBUTE.freeze();
        DataRegistries.SCOREBOARD_CRITERIA.reset();
        ObjectiveCriteria.getCustomCriteriaNames().forEach(it -> DataRegistries.SCOREBOARD_CRITERIA.set(it, new ScoreboardCriterionData(it, ObjectiveCriteria.byName(it).get().isReadOnly())));
        BuiltInRegistries.STAT_TYPE.forEach(it -> it.getRegistry().forEach(it2 -> {
            String string = Stat.buildName((StatType<Object>) it, it2);
            DataRegistries.SCOREBOARD_CRITERIA.set(string, new ScoreboardCriterionData(string, false));
        }));
        DataRegistries.SCOREBOARD_CRITERIA.freeze();
        DataRegistries.MOB_EFFECT.reset();
        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e -> DataRegistries.MOB_EFFECT.set(new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath()), new MobEffectData(new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath()), e.getValue().isInstantenous())));
        DataRegistries.MOB_EFFECT.freeze();
        DataRegistries.ENTITY_TYPE.reset();
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(e -> {
            DataLocation location = new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath());
            DataRegistries.ENTITY_TYPE.set(location, new EntityData(location, e.getValue().canSummon()));
        });
        DataRegistries.ENTITY_TYPE.freeze();
        DataRegistries.FUNCTION_TAGS.reset();
        for(var tagName : serverFunctionManager.getTagNames()) {
            var functions = serverFunctionManager.getTag(tagName);
            var mappedFunctions = new ArrayList<DataLocation>(functions.size());
            for(var function : functions) mappedFunctions.add(new DataLocation(function.id().getNamespace(), function.id().getPath()));
            DataRegistries.FUNCTION_TAGS.set(new DataLocation(tagName.getNamespace(), tagName.getPath()), List.copyOf(mappedFunctions));
        }
        DataRegistries.FUNCTION_TAGS.freeze();
        DataRegistries.ENTITY_TAGS.reset();
        BuiltInRegistries.ENTITY_TYPE.getTags().forEach(e -> {
            DataLocation location = new DataLocation(e.key().location().getNamespace(), e.key().location().getPath());
            var mapped = new ArrayList<DataLocation>();
            for(var item : e) {
                var key = EntityType.getKey(item.value());
                mapped.add(new DataLocation(key.getNamespace(), key.getPath()));
            }
            DataRegistries.ENTITY_TAGS.set(location, List.copyOf(mapped));
        });
        DataRegistries.ENTITY_TAGS.freeze();
        DataRegistries.ADVANCEMENT.reset();
        server.getAdvancements().getAllAdvancements().forEach(e -> {
            var location = new DataLocation(e.id().getNamespace(), e.id().getPath());
            var criteria = List.copyOf(e.value().criteria().keySet());
            DataRegistries.ADVANCEMENT.set(location, new AdvancementData(location, e.value().parent().map(resourceLocation -> new DataLocation(resourceLocation.getNamespace(), resourceLocation.getPath())).orElse(null), criteria));
        });
        DataRegistries.ADVANCEMENT.freeze();
    }
}
