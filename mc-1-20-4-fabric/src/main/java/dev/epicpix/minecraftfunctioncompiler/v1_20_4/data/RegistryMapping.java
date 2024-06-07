package dev.epicpix.minecraftfunctioncompiler.v1_20_4.data;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;

public final class RegistryMapping {
    private RegistryMapping() {}

    private static final HashMap<DataLocation, Class<?>> entityTypeMappings = new HashMap<>();

    public static Class<?> mapEntityTypeClass(DataLocation type) {
        return entityTypeMappings.get(type);
    }

    public static void updateRegistryMaps() throws IllegalAccessException {
        entityTypeMappings.clear();
        for(Field field : EntityType.class.getDeclaredFields()) {
            if(EntityType.class.isAssignableFrom(field.getType()) && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                var value = (EntityType<?>) field.get(null);
                var type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(value);
                entityTypeMappings.put(new DataLocation(key.getNamespace(), key.getPath()), type);
            }
        }
        entityTypeMappings.put(new DataLocation("minecraft", "player"), ServerPlayer.class);
        entityTypeMappings.put(new DataLocation("minecraftfunctioncompiler", "living_entity"), LivingEntity.class);
    }
}
