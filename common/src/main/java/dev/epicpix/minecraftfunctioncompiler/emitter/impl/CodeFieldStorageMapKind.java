package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG_DUMP_CLASS_FILES;

public final class CodeFieldStorageMapKind<T> {

    private static final ArrayList<CodeFieldStorageMapKind<?>> VALUES = new ArrayList<>();

    private final String kind;
    private final BiFunction<CodeFieldStorageStorageMap, T, String> nameGetter;
    private List<CodeFieldStorageMapKind<?>> dependencies = List.of();

    private void setDependencies(CodeFieldStorageMapKind<?>... dependencies) {
        this.dependencies = List.of(dependencies);
    }

    private CodeFieldStorageMapKind(String kind, BiFunction<CodeFieldStorageStorageMap, T, String> nameGetter) {
        this.kind = kind;
        this.nameGetter = nameGetter;
        VALUES.add(this);
    }

    public String getKind() {
        return kind;
    }

    public List<CodeFieldStorageMapKind<?>> getDependencies() {
        return dependencies;
    }

    public static final CodeFieldStorageMapKind<DataLocation> ADVANCEMENT = new CodeFieldStorageMapKind<>("ADVANCEMENT", CodeFieldStorageMapKind::getAdvancementName);
    public static final CodeFieldStorageMapKind<DataLocation> ATTRIBUTE = new CodeFieldStorageMapKind<>("ATTRIBUTE", CodeFieldStorageMapKind::getAttributeName);
    public static final CodeFieldStorageMapKind<DataLocation> EFFECT = new CodeFieldStorageMapKind<>("EFFECT", CodeFieldStorageMapKind::getEffectName);
    public static final CodeFieldStorageMapKind<DataLocation> ENTITY_TYPE = new CodeFieldStorageMapKind<>("ENTITY_TYPE", CodeFieldStorageMapKind::getEntityTypeName);
    public static final CodeFieldStorageMapKind<DataLocation> FUNCTION = new CodeFieldStorageMapKind<>("FUNCTION", CodeFieldStorageMapKind::getFunctionName);
    public static final CodeFieldStorageMapKind<NbtValue.Compound> NBT_COMPOUND = new CodeFieldStorageMapKind<>("NBT_COMPOUND", CodeFieldStorageMapKind::getNbtCompoundValueName);
    public static final CodeFieldStorageMapKind<NbtValue> NBT_VALUE = new CodeFieldStorageMapKind<>("NBT_VALUE", CodeFieldStorageMapKind::getNbtValueName);
    public static final CodeFieldStorageMapKind<DataLocation> PREDICATE = new CodeFieldStorageMapKind<>("REGEX_PATTERN", CodeFieldStorageMapKind::getPredicateName);
    public static final CodeFieldStorageMapKind<String> REGEX_PATTERN = new CodeFieldStorageMapKind<>("REGEX_PATTERN", CodeFieldStorageMapKind::getRegexPatternName);
    public static final CodeFieldStorageMapKind<DataLocation> RESOURCE_LOCATION = new CodeFieldStorageMapKind<>("RESOURCE_LOCATION", CodeFieldStorageMapKind::getResourceLocationName);
    public static final CodeFieldStorageMapKind<String> SCOREBOARD_CRITERION = new CodeFieldStorageMapKind<>("SCOREBOARD_CRITERION", CodeFieldStorageMapKind::getScoreboardCriterionName);
    public static final CodeFieldStorageMapKind<Text> TEXT = new CodeFieldStorageMapKind<>("TEXT", CodeFieldStorageMapKind::getTextName);
    public static final CodeFieldStorageMapKind<TextStyle> TEXT_STYLE = new CodeFieldStorageMapKind<>("TEXT_STYLE", CodeFieldStorageMapKind::getTextStyleName);
    public static final CodeFieldStorageMapKind<UUID> UUID = new CodeFieldStorageMapKind<>("UUID", CodeFieldStorageMapKind::getUuidName);

    static {
        ADVANCEMENT.setDependencies(RESOURCE_LOCATION);
        ATTRIBUTE.setDependencies(RESOURCE_LOCATION);
        EFFECT.setDependencies(RESOURCE_LOCATION);
        ENTITY_TYPE.setDependencies(RESOURCE_LOCATION);
        FUNCTION.setDependencies(RESOURCE_LOCATION);
        PREDICATE.setDependencies(RESOURCE_LOCATION);
        TEXT_STYLE.setDependencies(RESOURCE_LOCATION);
        TEXT.setDependencies(TEXT_STYLE);
    }

    @Override
    public String toString() {
        return getKind();
    }

    public static CodeFieldStorageMapKind<?>[] values() {
        return VALUES.toArray(new CodeFieldStorageMapKind<?>[0]);
    }

    public String generateName(CodeFieldStorageStorageMap map, T value) {
        return nameGetter.apply(map, value);
    }



    private static String getAdvancementName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "advancement$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "A" + map.checkIndex(CodeFieldStorageMapKind.ADVANCEMENT, value);
    }

    private static String getAttributeName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "attribute$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "a" + map.checkIndex(CodeFieldStorageMapKind.ATTRIBUTE, value);
    }

    private static String getEffectName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "effect$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "e" + map.checkIndex(CodeFieldStorageMapKind.EFFECT, value);
    }

    private static String getEntityTypeName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "entityType$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "T" + map.checkIndex(CodeFieldStorageMapKind.ENTITY_TYPE, value);
    }

    private static String getFunctionName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "function$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "f" + map.checkIndex(CodeFieldStorageMapKind.FUNCTION, value);
    }

    private static String getNbtValueName(CodeFieldStorageStorageMap map, NbtValue value) {
        return "nv" + map.checkIndex(CodeFieldStorageMapKind.NBT_VALUE, value);
    }

    private static String getNbtCompoundValueName(CodeFieldStorageStorageMap map, NbtValue.Compound value) {
        return "nc" + map.checkIndex(CodeFieldStorageMapKind.NBT_COMPOUND, value);
    }

    private static String getPredicateName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "predicate$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "p" + map.checkIndex(CodeFieldStorageMapKind.PREDICATE, value);
    }

    private static String getRegexPatternName(CodeFieldStorageStorageMap map, String value) {
        return "R" + map.checkIndex(CodeFieldStorageMapKind.REGEX_PATTERN, value);
    }

    private static String getResourceLocationName(CodeFieldStorageStorageMap map, DataLocation value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "resourceLocation$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
            : "r" + map.checkIndex(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
    }

    private static String getScoreboardCriterionName(CodeFieldStorageStorageMap map, String value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "scoreboardCriterion$" + value.replace(':', '$').replace('.', '$')
            : "s" + map.checkIndex(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
    }

    private static String getTextName(CodeFieldStorageStorageMap map, Text value) {
        return "t" + map.checkIndex(CodeFieldStorageMapKind.TEXT, value);
    }

    private static String getTextStyleName(CodeFieldStorageStorageMap map, TextStyle value) {
        return "ts" + map.checkIndex(CodeFieldStorageMapKind.TEXT_STYLE, value);
    }

    private static String getUuidName(CodeFieldStorageStorageMap map, UUID value) {
        return DEBUG_DUMP_CLASS_FILES
            ? "uuid$" + value.toString().replace("-", "")
            : "u" + map.checkIndex(CodeFieldStorageMapKind.UUID, value);
    }
}
