package dev.epicpix.minecraftfunctioncompiler.v1_21_2;

import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class RegistryMapping {
    private RegistryMapping() {}

    @SuppressWarnings("unchecked")
    public static void updateRegistryMaps(ServerFunctionManager serverFunctionManager) throws IllegalAccessException {
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
        DataRegistries.ENTITY_CLASS.reset();
        HashMap<ResourceLocation, Class<?>> definedEntityClasses = new HashMap<>();
        HashSet<Class<?>> duplicateEntityClasses = new HashSet<>();
        for(Field field : EntityType.class.getDeclaredFields()) {
            if(EntityType.class.isAssignableFrom(field.getType()) && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                var value = (EntityType<?>) field.get(null);
                var type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(value);
                if(definedEntityClasses.containsValue(type)) {
                    duplicateEntityClasses.add(type);
                    continue;
                }
                definedEntityClasses.put(key, type);
            }
        }
        for(var entry : definedEntityClasses.entrySet()) {
            if(!duplicateEntityClasses.contains(entry.getValue())) {
                var entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entry.getKey());
                DataRegistries.ENTITY_CLASS.set(new DataLocation(entry.getKey().getNamespace(), entry.getKey().getPath()), new EntityData(Type.getType(entry.getValue()), entityType.canSummon()));
            }
        }
        DataRegistries.ENTITY_CLASS.set(new DataLocation("minecraft", "player"), new EntityData(Type.getType(ServerPlayer.class), false));
        DataRegistries.ENTITY_CLASS.freeze();
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
    }
}
