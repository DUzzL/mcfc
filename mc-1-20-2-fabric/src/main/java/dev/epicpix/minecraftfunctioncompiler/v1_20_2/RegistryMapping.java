package dev.epicpix.minecraftfunctioncompiler.v1_20_2;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;

public final class RegistryMapping {
    private RegistryMapping() {}

    public static void updateRegistryMaps() throws IllegalAccessException {
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
        DataRegistries.MOB_EFFECT.reset();
        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e -> DataRegistries.MOB_EFFECT.set(new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath()), new MobEffectData(new DataLocation(e.getKey().location().getNamespace(), e.getKey().location().getPath()), e.getValue().isInstantenous())));
        DataRegistries.MOB_EFFECT.freeze();
        DataRegistries.ENTITY_CLASS.reset();
        for(Field field : EntityType.class.getDeclaredFields()) {
            if(EntityType.class.isAssignableFrom(field.getType()) && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                var value = (EntityType<?>) field.get(null);
                var type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(value);
                DataRegistries.ENTITY_CLASS.set(new DataLocation(key.getNamespace(), key.getPath()), type);
            }
        }
        DataRegistries.ENTITY_CLASS.set(new DataLocation("minecraft", "player"), ServerPlayer.class);
        DataRegistries.ENTITY_CLASS.freeze();
    }
}
