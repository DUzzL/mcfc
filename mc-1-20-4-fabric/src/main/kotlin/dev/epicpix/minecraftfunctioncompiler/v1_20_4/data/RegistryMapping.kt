package dev.epicpix.minecraftfunctioncompiler.v1_20_4.data

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.toDataLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

private val entityTypeMappings: MutableMap<DataLocation, Class<*>> = mutableMapOf()

fun mapEntityTypeClass(type: DataLocation) = entityTypeMappings[type]

fun updateRegistryMaps() {
    entityTypeMappings.clear()
    for(field in EntityType::class.java.declaredFields) {
        if(EntityType::class.java.isAssignableFrom(field.type) && Modifier.isPublic(field.modifiers) && Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers)) {
            val value = field.get(null) as EntityType<*>
            val type = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
            entityTypeMappings[BuiltInRegistries.ENTITY_TYPE.getKey(value).toDataLocation()] = type
        }
    }
    entityTypeMappings[DataLocation("minecraft", "player")] = ServerPlayer::class.java
    entityTypeMappings[DataLocation("minecraftfunctioncompiler", "living_entity")] = LivingEntity::class.java
}