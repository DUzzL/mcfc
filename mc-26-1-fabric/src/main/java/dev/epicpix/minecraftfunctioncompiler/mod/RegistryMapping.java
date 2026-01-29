package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.data.AdvancementData;
import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.EntityData;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;
import dev.epicpix.minecraftfunctioncompiler.data.PredicateData;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import dev.epicpix.minecraftfunctioncompiler.registry.TaggableDataRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

public final class RegistryMapping {
    private RegistryMapping() {}

    private static DataLocation fromIdentifier(Identifier identifier) {
        return new DataLocation(identifier.getNamespace(), identifier.getPath());
    }

    private static <T, V> void mapRegistry(TaggableDataRegistry<V> registry, HolderLookup.RegistryLookup<T> lookup, BiFunction<DataLocation, T, V> valueMapper) {
        var reverseMap = new HashMap<T, Identifier>();
        lookup.listElements().forEach(ref -> {
            reverseMap.put(ref.value(), ref.key().identifier());
            var location = fromIdentifier(ref.key().identifier());
            registry.setElement(location, valueMapper.apply(location, ref.value()));
        });
        lookup.listTags().forEach(e -> {
            var mapped = new ArrayList<DataLocation>();
            for(var item : e) {
                mapped.add(fromIdentifier(reverseMap.get(item.value())));
            }
            registry.setTag(fromIdentifier(e.key().location()), List.copyOf(mapped));
        });
    }

    @SuppressWarnings("unchecked")
    public static void updateRegistryMaps(MinecraftServer server, ServerFunctionManager serverFunctionManager) throws IllegalAccessException {
        DataRegistries.ATTRIBUTE.reset();
        mapRegistry(DataRegistries.ATTRIBUTE, BuiltInRegistries.ATTRIBUTE, (location, value) -> {
            if(value instanceof RangedAttribute ranged) {
                return new AttributeData(location, value.getDefaultValue(), ranged.getMinValue(), ranged.getMaxValue());
            }else {
                return new AttributeData(location, value.getDefaultValue(), Double.MIN_VALUE, Double.MAX_VALUE);
            }
        });
        DataRegistries.ATTRIBUTE.freeze();


        DataRegistries.SCOREBOARD_CRITERIA.reset();
        ObjectiveCriteria.getCustomCriteriaNames().forEach(it -> {
            DataRegistries.SCOREBOARD_CRITERIA.set(it, new ScoreboardCriterionData(it, ObjectiveCriteria.byName(it).orElseThrow().isReadOnly()));
        });
        BuiltInRegistries.STAT_TYPE.forEach(it -> it.getRegistry().forEach(it2 -> {
            String string = Stat.buildName((StatType<Object>) it, it2);
            DataRegistries.SCOREBOARD_CRITERIA.set(string, new ScoreboardCriterionData(string, false));
        }));
        DataRegistries.SCOREBOARD_CRITERIA.freeze();


        DataRegistries.MOB_EFFECT.reset();
        mapRegistry(DataRegistries.MOB_EFFECT, BuiltInRegistries.MOB_EFFECT, (location, value) -> new MobEffectData(location, value.isInstantenous()));
        DataRegistries.MOB_EFFECT.freeze();


        DataRegistries.ENTITY_TYPE.reset();
        mapRegistry(DataRegistries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE, (location, value) -> new EntityData(location, value.canSummon()));
        DataRegistries.ENTITY_TYPE.freeze();


        DataRegistries.FUNCTION_TAGS.reset();
        for(var tagName : serverFunctionManager.getTagNames()) {
            var functions = serverFunctionManager.getTag(tagName);
            var mappedFunctions = new ArrayList<DataLocation>(functions.size());
            for(var function : functions) mappedFunctions.add(fromIdentifier(function.id()));
            DataRegistries.FUNCTION_TAGS.set(fromIdentifier(tagName), List.copyOf(mappedFunctions));
        }
        DataRegistries.FUNCTION_TAGS.freeze();


        DataRegistries.ADVANCEMENT.reset();
        server.getAdvancements().getAllAdvancements().forEach(e -> {
            var location = fromIdentifier(e.id());
            var criteria = List.copyOf(e.value().criteria().keySet());
            DataRegistries.ADVANCEMENT.setElement(location, new AdvancementData(location, e.value().parent().map(RegistryMapping::fromIdentifier).orElse(null), criteria));
        });
        DataRegistries.ADVANCEMENT.freeze();


        DataRegistries.PREDICATE.reset();
        mapRegistry(DataRegistries.PREDICATE, server.reloadableRegistries().lookup().lookup(Registries.PREDICATE).orElseThrow(), (location, _) -> new PredicateData(location));
        DataRegistries.PREDICATE.freeze();
    }
}
