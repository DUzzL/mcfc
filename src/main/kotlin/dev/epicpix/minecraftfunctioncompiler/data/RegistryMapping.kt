package dev.epicpix.minecraftfunctioncompiler.data

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

private val entityTypeMappings: MutableMap<ResourceLocation, Class<*>> = mutableMapOf()

fun mapEntityTypeClass(type: ResourceLocation) = entityTypeMappings[type]

fun updateRegistryMaps() {
    entityTypeMappings.clear()
    for(field in EntityType::class.java.declaredFields) {
        if(EntityType::class.java.isAssignableFrom(field.type) && Modifier.isPublic(field.modifiers) && Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers)) {
            val value = field.get(null) as EntityType<*>
            val type = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
            entityTypeMappings[BuiltInRegistries.ENTITY_TYPE.getKey(value)] = type
        }
    }
}