package dev.epicpix.minecraftfunctioncompiler.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.ALLOW_EXTERNAL_DATA_ACCESS;

public final class DataRegistries {
    private DataRegistries() {}

    public static final DataRegistry<AttributeData> ATTRIBUTE = new DataRegistry<>();
    public static final DataRegistry<EntityData> ENTITY_CLASS = new DataRegistry<>();
    public static final DataRegistry<List<DataLocation>> ENTITY_TAGS = new DataRegistry<>();
    public static final DataRegistry<List<DataLocation>> FUNCTION_TAGS = new DataRegistry<>();
    public static final DataRegistry<MobEffectData> MOB_EFFECT = new DataRegistry<>();

    public static final RawDataRegistry<String, ScoreboardCriterionData> SCOREBOARD_CRITERIA = new RawDataRegistry<>();

    public static JsonObject extractRegistries() {
        if(!ALLOW_EXTERNAL_DATA_ACCESS) throw new UnsupportedOperationException("This build doesn't support extracting registries");
        var registries = new JsonObject();

        var attributeRegistry = new JsonObject();
        for(var entry : ATTRIBUTE.entrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("default_value", data.defaultValue());
            obj.addProperty("minimum_value", data.minimumValue());
            obj.addProperty("maximum_value", data.maximumValue());
            attributeRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ATTRIBUTE", attributeRegistry);

        var entityClassRegistry = new JsonObject();
        for(var entry : ENTITY_CLASS.entrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("type", data.type().getDescriptor());
            obj.addProperty("summonable", data.summonable());
            entityClassRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("ENTITY_CLASS", entityClassRegistry);

        var entityTagsRegistry = new JsonObject();
        for(var entry : ENTITY_TAGS.entrySet()) {
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
        for(var entry : MOB_EFFECT.entrySet()) {
            var data = entry.getValue();
            var obj = new JsonObject();
            obj.addProperty("instant", data.instant());
            mobEffectRegistry.add(entry.getKey().toString(), obj);
        }
        registries.add("MOB_EFFECT", mobEffectRegistry);

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

        ATTRIBUTE.reset();
        for(var attributes : registries.getAsJsonObject("ATTRIBUTE").entrySet()) {
            var attribute = attributes.getValue().getAsJsonObject();
            var location = new DataLocation(attributes.getKey());
            ATTRIBUTE.set(location, new AttributeData(location, attribute.get("default_value").getAsDouble(), attribute.get("minimum_value").getAsDouble(), attribute.get("maximum_value").getAsDouble()));
        }
        ATTRIBUTE.freeze();

        ENTITY_CLASS.reset();
        for(var entityClasses : registries.getAsJsonObject("ENTITY_CLASS").entrySet()) {
            var entityClass = entityClasses.getValue().getAsJsonObject();
            var location = new DataLocation(entityClasses.getKey());
            ENTITY_CLASS.set(location, new EntityData(Type.getType(entityClass.get("type").getAsString()), entityClass.get("summonable").getAsBoolean()));
        }
        ENTITY_CLASS.freeze();

        ENTITY_TAGS.reset();
        for(var entityTags : registries.getAsJsonObject("ENTITY_TAGS").entrySet()) {
            var functionLocations = entityTags.getValue().getAsJsonArray();
            var location = new DataLocation(entityTags.getKey());
            var entities = new ArrayList<DataLocation>();
            for(var loc : functionLocations) entities.add(new DataLocation(loc.getAsString()));
            ENTITY_TAGS.set(location, List.copyOf(entities));
        }
        ENTITY_TAGS.freeze();

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
            MOB_EFFECT.set(location, new MobEffectData(location, mobEffect.get("instant").getAsBoolean()));
        }
        MOB_EFFECT.freeze();

        SCOREBOARD_CRITERIA.reset();
        for(var scoreboardCriteria : registries.getAsJsonObject("SCOREBOARD_CRITERIA").entrySet()) {
            var scoreboardCriterion = scoreboardCriteria.getValue().getAsJsonObject();
            var location = scoreboardCriteria.getKey();
            SCOREBOARD_CRITERIA.set(location, new ScoreboardCriterionData(location, scoreboardCriterion.get("read_only").getAsBoolean()));
        }
        SCOREBOARD_CRITERIA.freeze();
    }

}
