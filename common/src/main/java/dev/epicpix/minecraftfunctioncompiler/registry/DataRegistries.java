package dev.epicpix.minecraftfunctioncompiler.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.data.AdvancementData;
import dev.epicpix.minecraftfunctioncompiler.data.AttributeData;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.EntityData;
import dev.epicpix.minecraftfunctioncompiler.data.MobEffectData;
import dev.epicpix.minecraftfunctioncompiler.data.PredicateData;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCriterionData;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ALLOW_EXTERNAL_DATA_ACCESS;

public final class DataRegistries {
    private DataRegistries() {}

    public static final TaggableDataRegistry<AdvancementData> ADVANCEMENT = new TaggableDataRegistry<>();
    public static final TaggableDataRegistry<AttributeData> ATTRIBUTE = new TaggableDataRegistry<>();
    public static final TaggableDataRegistry<EntityData> ENTITY_TYPE = new TaggableDataRegistry<>();
    public static final DataRegistry<List<DataLocation>> FUNCTION_TAGS = new DataRegistry<>();
    public static final TaggableDataRegistry<MobEffectData> MOB_EFFECT = new TaggableDataRegistry<>();
    public static final TaggableDataRegistry<PredicateData> PREDICATE = new TaggableDataRegistry<>();

    public static final RawDataRegistry<String, ScoreboardCriterionData> SCOREBOARD_CRITERIA = new RawDataRegistry<>();

    public static JsonObject extractRegistries() {
        if(!ALLOW_EXTERNAL_DATA_ACCESS) throw new UnsupportedOperationException("This build doesn't support extracting registries");
        var registries = new JsonObject();

        var advancementRegistry = new JsonObject();
        for(var entry : ADVANCEMENT.elementEntrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("parent", data.parent() == null ? null : data.parent().toString());
            var criteria = new JsonArray();
            for(var v : data.criteria()) {
                criteria.add(v);
            }
            obj.add("criteria", criteria);
            advancementRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ADVANCEMENT", advancementRegistry);

        var attributeRegistry = new JsonObject();
        for(var entry : ATTRIBUTE.elementEntrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("default_value", data.defaultValue());
            obj.addProperty("minimum_value", data.minimumValue());
            obj.addProperty("maximum_value", data.maximumValue());
            attributeRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ATTRIBUTE", attributeRegistry);

        var entityClassRegistry = new JsonObject();
        for(var entry : ENTITY_TYPE.elementEntrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("summonable", data.summonable());
            entityClassRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ENTITY_TYPE", entityClassRegistry);

        var entityTagsRegistry = new JsonObject();
        for(var entry : ENTITY_TYPE.tagsEntrySet()) {
            var data = entry.getValue();
            var obj = new JsonArray();
            for(var entity : data) {
                obj.add(entity.toString());
            }
            entityTagsRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ENTITY_TAGS", entityTagsRegistry);

        var functionTagsRegistry = new JsonObject();
        for(var entry : FUNCTION_TAGS.entrySet()) {
            var data = entry.getValue();
            var obj = new JsonArray();
            for(var func : data) {
                obj.add(func.toString());
            }
            functionTagsRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("FUNCTION_TAGS", functionTagsRegistry);

        var mobEffectRegistry = new JsonObject();
        for(var entry : MOB_EFFECT.elementEntrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("instant", data.instant());
            mobEffectRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("MOB_EFFECT", mobEffectRegistry);

        var predicateRegistry = new JsonObject();
        for(var entry : PREDICATE.elementEntrySet()) {
            predicateRegistry.add(entry.getKey().toString(), new JsonObject());
        }
        registries.add("PREDICATE", predicateRegistry);

        var scoreboardCriteriaRegistry = new JsonObject();
        for(var entry : SCOREBOARD_CRITERIA.entrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("read_only", data.readOnly());
            scoreboardCriteriaRegistry.add(entry.getKey(), obj);
        }
        registries.add("SCOREBOARD_CRITERIA", scoreboardCriteriaRegistry);

        return registries;
    }

    public static void insertRegistries(JsonObject registries) {
        if(!ALLOW_EXTERNAL_DATA_ACCESS) throw new UnsupportedOperationException("This build doesn't support inserting registries from json");

        ADVANCEMENT.reset();
        for(var advancements : registries.getAsJsonObject("ADVANCEMENT").entrySet()) {
            var advancement = advancements.getValue().getAsJsonObject();
            var location = new DataLocation(advancements.getKey());
            var criteria = new ArrayList<String>();
            for(var name : advancement.getAsJsonArray("criteria")) criteria.add(name.getAsString());
            ADVANCEMENT.setElement(location, new AdvancementData(location, advancement.get("parent").isJsonNull() ? null : new DataLocation(advancement.get("parent").getAsString()), List.copyOf(criteria)));
        }
        ADVANCEMENT.freeze();

        ATTRIBUTE.reset();
        for(var attributes : registries.getAsJsonObject("ATTRIBUTE").entrySet()) {
            var attribute = attributes.getValue().getAsJsonObject();
            var location = new DataLocation(attributes.getKey());
            ATTRIBUTE.setElement(location, new AttributeData(location, attribute.get("default_value").getAsDouble(), attribute.get("minimum_value").getAsDouble(), attribute.get("maximum_value").getAsDouble()));
        }
        ATTRIBUTE.freeze();

        ENTITY_TYPE.reset();
        for(var entityClasses : registries.getAsJsonObject("ENTITY_TYPE").entrySet()) {
            var entityClass = entityClasses.getValue().getAsJsonObject();
            var location = new DataLocation(entityClasses.getKey());
            ENTITY_TYPE.setElement(location, new EntityData(location, entityClass.get("summonable").getAsBoolean()));
        }
        for(var entityTags : registries.getAsJsonObject("ENTITY_TAGS").entrySet()) {
            var functionLocations = entityTags.getValue().getAsJsonArray();
            var location = new DataLocation(entityTags.getKey());
            var entities = new ArrayList<DataLocation>();
            for(var loc : functionLocations) entities.add(new DataLocation(loc.getAsString()));
            ENTITY_TYPE.setTag(location, List.copyOf(entities));
        }
        ENTITY_TYPE.freeze();

        FUNCTION_TAGS.reset();
        for(var functionTags : registries.getAsJsonObject("FUNCTION_TAGS").entrySet()) {
            var functionLocations = functionTags.getValue().getAsJsonArray();
            var location = new DataLocation(functionTags.getKey());
            var functions = new ArrayList<DataLocation>();
            for(var loc : functionLocations) functions.add(new DataLocation(loc.getAsString()));
            FUNCTION_TAGS.set(location, List.copyOf(functions));
        }
        FUNCTION_TAGS.freeze();

        MOB_EFFECT.reset();
        for(var mobEffects : registries.getAsJsonObject("MOB_EFFECT").entrySet()) {
            var mobEffect = mobEffects.getValue().getAsJsonObject();
            var location = new DataLocation(mobEffects.getKey());
            MOB_EFFECT.setElement(location, new MobEffectData(location, mobEffect.get("instant").getAsBoolean()));
        }
        MOB_EFFECT.freeze();

        PREDICATE.reset();
        for(var predicates : registries.getAsJsonObject("PREDICATE").entrySet()) {
            var location = new DataLocation(predicates.getKey());
            PREDICATE.setElement(location, new PredicateData(location));
        }
        PREDICATE.freeze();

        SCOREBOARD_CRITERIA.reset();
        for(var scoreboardCriteria : registries.getAsJsonObject("SCOREBOARD_CRITERIA").entrySet()) {
            var scoreboardCriterion = scoreboardCriteria.getValue().getAsJsonObject();
            var location = scoreboardCriteria.getKey();
            SCOREBOARD_CRITERIA.set(location, new ScoreboardCriterionData(location, scoreboardCriterion.get("read_only").getAsBoolean()));
        }
        SCOREBOARD_CRITERIA.freeze();
    }

}
