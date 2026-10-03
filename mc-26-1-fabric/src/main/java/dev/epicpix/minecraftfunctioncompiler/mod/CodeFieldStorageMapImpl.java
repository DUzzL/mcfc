package dev.epicpix.minecraftfunctioncompiler.mod;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMap;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMap_get;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callOptional_get;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.loadBoolean_FALSE;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.loadBoolean_TRUE;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callByteTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callCollectionTag_addTag;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_keybind;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_literal;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_translatableArguments;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_translatableEmpty;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_translatableFallbackArguments;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callComponent_translatableFallbackEmpty;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callCompoundTag_put;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callDoubleTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callFloatTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callHolderLookupProvider_get;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callHolder_value;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callIdentifier_fromNamespaceAndPath;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callIntTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callInteger_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callLongTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callMinecraftServer_getAdvancements;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callMinecraftServer_reloadableRegistries;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callMutableComponent_append;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callMutableComponent_withStyle;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callObjectiveCriteria_byName;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callRegistry_get;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callRegistry_getValue;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callReloadableServerRegistriesHolder_lookup;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callResourceKey_create;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callServerAdvancementManager_get;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callShortTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callStringTag_valueOf;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.callTextColor_fromRgb;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadBuiltInRegistries_ATTRIBUTE;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadBuiltInRegistries_ENTITY_TYPE;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadBuiltInRegistries_MOB_EFFECT;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadRegistries_PREDICATE;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionLibrary_functions;
import static dev.epicpix.minecraftfunctioncompiler.mod.emitter.MappedUsageDefinitionsExtensions.loadServerFunctionManager_library;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl().addDefaults();

    private static final InstructionTarget STYLE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(Style.class, TextColor.class, Integer.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, ClickEvent.class, HoverEvent.class, String.class, FontDescription.class);
    private static final InstructionTarget FONT_DESCRIPTION_RESOURCE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(FontDescription.Resource.class, Identifier.class);
    private static final InstructionTarget CLICK_RUN_COMMAND_INIT = InstructionTarget.FunctionTarget.initFunction(ClickEvent.RunCommand.class, String.class);
    private static final InstructionTarget CLICK_SUGGEST_COMMAND_INIT = InstructionTarget.FunctionTarget.initFunction(ClickEvent.SuggestCommand.class, String.class);
    private static final InstructionTarget CLICK_COPY_INIT = InstructionTarget.FunctionTarget.initFunction(ClickEvent.CopyToClipboard.class, String.class);
    private static final InstructionTarget CLICK_CHANGE_PAGE_INIT = InstructionTarget.FunctionTarget.initFunction(ClickEvent.ChangePage.class, int.class);
    private static final InstructionTarget URI_CREATE = new InstructionTarget.FunctionTarget(java.net.URI.class.describeConstable().orElseThrow(), "create", java.lang.constant.MethodTypeDesc.of(java.net.URI.class.describeConstable().orElseThrow(), String.class.describeConstable().orElseThrow()), true, false, false, true);
    private static final InstructionTarget CLICK_OPEN_URL_INIT = InstructionTarget.FunctionTarget.initFunction(ClickEvent.OpenUrl.class, java.net.URI.class);
    private static final InstructionTarget HOVER_SHOW_TEXT_INIT = InstructionTarget.FunctionTarget.initFunction(HoverEvent.ShowText.class, Component.class);
    private static final InstructionTarget LIST_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ListTag.class);
    private static final InstructionTarget BYTE_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ByteArrayTag.class, byte[].class);
    private static final InstructionTarget COMPOUND_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompoundTag.class);
    private static final InstructionTarget INT_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(IntArrayTag.class, int[].class);
    private static final InstructionTarget LONG_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(LongArrayTag.class, long[].class);
    private static final InstructionTarget OBJECTIVE_REF_INIT = InstructionTarget.FunctionTarget.initFunction(ObjectiveRef.class, String.class);
    private static final InstructionTarget SCORE_REF_INIT = InstructionTarget.FunctionTarget.initFunction(ScoreRef.class, String.class, String.class);
    private static final InstructionTarget SCORE_HOLDER_FOR_NAME_ONLY = new InstructionTarget.FunctionTarget(net.minecraft.world.scores.ScoreHolder.class.describeConstable().orElseThrow(), "forNameOnly", java.lang.constant.MethodTypeDesc.of(net.minecraft.world.scores.ScoreHolder.class.describeConstable().orElseThrow(), String.class.describeConstable().orElseThrow()), true, true, false, true);
    private static final InstructionTarget OBJECTIVE_REF_AT = InstructionTarget.FunctionTarget.staticFunction(ObjectiveRef.class, "at", ObjectiveRef.class, ObjectiveRef[].class, int.class);
    private static final InstructionTarget SCORE_REF_AT = InstructionTarget.FunctionTarget.staticFunction(ScoreRef.class, "at", ScoreRef.class, ScoreRef[].class, int.class);

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ADVANCEMENT, new CodeFieldStorageMapValue<>(AdvancementHolder.class, (cf, cw, value) -> {
            return callServerAdvancementManager_get(cw, callMinecraftServer_getAdvancements(cw, cf.getGeneratorMinecraftServer()), cf.loadIdentifier(cw, value));
        }));
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_get(cw, loadBuiltInRegistries_ATTRIBUTE(cw), cf.loadIdentifier(cw, value))), Holder.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_get(cw, loadBuiltInRegistries_MOB_EFFECT(cw), cf.loadIdentifier(cw, value))), Holder.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.ENTITY_TYPE, new CodeFieldStorageMapValue<>(EntityType.class, (cf, cw, value) -> {
            return cw.castType(callRegistry_getValue(cw, loadBuiltInRegistries_ENTITY_TYPE(cw), cf.loadIdentifier(cw, value)), EntityType.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.FUNCTION, new CodeFieldStorageMapValue<>(CommandFunction.class, (cf, cw, value) -> {
            var functions = loadServerFunctionLibrary_functions(cw, loadServerFunctionManager_library(cw, cf.loadServerFunctionManager(cw)));
            return cw.castType(callMap_get(cw, functions, cf.loadIdentifier(cw, value)), CommandFunction.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.NBT_COMPOUND, new CodeFieldStorageMapValue<>(CompoundTag.class, (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.NBT_VALUE, new CodeFieldStorageMapValue<>(Tag.class, (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.OBJECTIVE_REF, new CodeFieldStorageMapValue<>(ObjectiveRef.class, (cf, cw, value) -> cw.newObjectCreate(OBJECTIVE_REF_INIT, new BytecodeValue.StringValue(value)), OBJECTIVE_REF_AT));
        set(CodeFieldStorageMapKind.PREDICATE, new CodeFieldStorageMapValue<>(LootItemCondition.class, (cf, cw, value) -> {
            var resourceKey = callResourceKey_create(cw, loadRegistries_PREDICATE(cw), cf.loadIdentifier(cw, value));
            return cw.castType(callHolder_value(cw, callOptional_get(cw, callHolderLookupProvider_get(cw, callReloadableServerRegistriesHolder_lookup(cw, callMinecraftServer_reloadableRegistries(cw, cf.getGeneratorMinecraftServer())), resourceKey))), LootItemCondition.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(Identifier.class, (cf, cw, value) -> {
            return callIdentifier_fromNamespaceAndPath(cw, new BytecodeValue.StringValue(value.namespace()), new BytecodeValue.StringValue(value.path()));
        }));
        set(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, new CodeFieldStorageMapValue<>(ObjectiveCriteria.class, (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callObjectiveCriteria_byName(cw, new BytecodeValue.StringValue(value))), ObjectiveCriteria.class.describeConstable().orElseThrow());
        }));
        set(CodeFieldStorageMapKind.SCORE_HOLDER, new CodeFieldStorageMapValue<>(net.minecraft.world.scores.ScoreHolder.class, (cf, cw, value) -> cw.writeTarget(SCORE_HOLDER_FOR_NAME_ONLY, new BytecodeValue.StringValue(value))));
        set(CodeFieldStorageMapKind.SCORE_REF, new CodeFieldStorageMapValue<>(ScoreRef.class, (cf, cw, value) -> cw.newObjectCreate(SCORE_REF_INIT, new BytecodeValue.StringValue(value.holder()), new BytecodeValue.StringValue(value.objective())), SCORE_REF_AT));
        set(CodeFieldStorageMapKind.TEXT, new CodeFieldStorageMapValue<>(Component.class, (cf, cw, value) -> writeTextValue(value, cw, cf)));
        set(CodeFieldStorageMapKind.TEXT_STYLE, new CodeFieldStorageMapValue<>(Style.class, (cf, cw, value) -> {
            ArrayList<BytecodeValue> args = new ArrayList<>();
            if(value.color() != null) {
                args.add(callTextColor_fromRgb(cw, new BytecodeValue.IntegerValue(value.color())));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            if(value.shadowColor() != null) {
                args.add(callInteger_valueOf(cw, new BytecodeValue.IntegerValue(value.shadowColor())));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            args.add(writeBoolean(cw, value.bold()));
            args.add(writeBoolean(cw, value.italic()));
            args.add(writeBoolean(cw, value.underlined()));
            args.add(writeBoolean(cw, value.strikethrough()));
            args.add(writeBoolean(cw, value.obfuscated()));
            // clickEvent
            if(value.clickEvent() != null) {
                var ce = value.clickEvent();
                args.add(switch(ce.action()) {
                    case RUN_COMMAND -> cw.newObjectCreate(CLICK_RUN_COMMAND_INIT, new BytecodeValue.StringValue(ce.value()));
                    case SUGGEST_COMMAND -> cw.newObjectCreate(CLICK_SUGGEST_COMMAND_INIT, new BytecodeValue.StringValue(ce.value()));
                    case COPY_TO_CLIPBOARD -> cw.newObjectCreate(CLICK_COPY_INIT, new BytecodeValue.StringValue(ce.value()));
                    case CHANGE_PAGE -> cw.newObjectCreate(CLICK_CHANGE_PAGE_INIT, new BytecodeValue.IntegerValue(Integer.parseInt(ce.value())));
                    case OPEN_URL -> cw.newObjectCreate(CLICK_OPEN_URL_INIT, cw.writeTarget(URI_CREATE, new BytecodeValue.StringValue(ce.value())));
                });
            } else {
                args.add(new BytecodeValue.NullValue());
            }
            // hoverEvent
            if(value.hoverEvent() != null && value.hoverEvent().action() == dev.epicpix.minecraftfunctioncompiler.data.text.TextHoverEvent.Action.SHOW_TEXT) {
                var hoverText = (Text) value.hoverEvent().value();
                args.add(cw.newObjectCreate(HOVER_SHOW_TEXT_INIT, writeTextValue(hoverText, cw, cf)));
            } else {
                args.add(new BytecodeValue.NullValue());
            }
            if(value.insertion() != null) {
                args.add(new BytecodeValue.StringValue(value.insertion()));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            if(value.font() != null) {
                args.add(cw.newObjectCreate(FONT_DESCRIPTION_RESOURCE_INIT_TARGET, cf.loadIdentifier(cw, value.font())));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            return cw.newObjectCreate(STYLE_INIT_TARGET, args);
        }));
    }

    private static BytecodeValue writeBoolean(CodeWriter cw, @Nullable Boolean value) {
        if(value == null) {
            return new BytecodeValue.NullValue();
        }else {
            if(value) {
                return loadBoolean_TRUE(cw);
            }else {
                return loadBoolean_FALSE(cw);
            }
        }
    }

    private static BytecodeValue writeNbtValue(NbtValue input, CodeWriter cw) {
        return switch(input) {
            case NbtValue.Array(List<NbtValue> values) -> {
                var listTag = cw.newObjectCreate(LIST_TAG_INIT_TARGET);
                for(int i = 0; i < values.size(); i++) {
                    callCollectionTag_addTag(cw, listTag, new BytecodeValue.IntegerValue(i), writeNbtValue(values.get(i), cw));
                }
                yield listTag;
            }
            case NbtValue.ByteArray(byte[] values) -> {
                var byteArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), ConstantDescs.CD_byte);
                for(int i = 0; i < values.length; i++) {
                    cw.setArrayValue(byteArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.IntegerValue(values[i]));
                }
                yield cw.newObjectCreate(BYTE_ARRAY_TAG_INIT_TARGET, byteArray);
            }
            case NbtValue.ByteLiteral(byte value) -> callByteTag_valueOf(cw, new BytecodeValue.IntegerValue(value));
            case NbtValue.Compound(Map<String, NbtValue> values) -> {
                var compoundTag = cw.newObjectCreate(COMPOUND_TAG_INIT_TARGET);
                for(var entry : values.entrySet()) {
                    callCompoundTag_put(cw, compoundTag, new BytecodeValue.StringValue(entry.getKey()), writeNbtValue(entry.getValue(), cw));
                }
                yield compoundTag;
            }
            case NbtValue.DoubleLiteral(double value) -> callDoubleTag_valueOf(cw, new BytecodeValue.DoubleValue(value));
            case NbtValue.FloatLiteral(float value) -> callFloatTag_valueOf(cw, new BytecodeValue.FloatValue(value));
            case NbtValue.IntegerArray(int[] values) -> {
                var intArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), ConstantDescs.CD_int);
                for(int i = 0; i < values.length; i++) {
                    cw.setArrayValue(intArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.IntegerValue(values[i]));
                }
                yield cw.newObjectCreate(INT_ARRAY_TAG_INIT_TARGET, intArray);
            }
            case NbtValue.IntegerLiteral(int value) -> callIntTag_valueOf(cw, new BytecodeValue.IntegerValue(value));
            case NbtValue.LongArray(long[] values) -> {
                var longArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), ConstantDescs.CD_long);
                for(int i = 0; i < values.length; i++) {
                    cw.setArrayValue(longArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.LongValue(values[i]));
                }
                yield cw.newObjectCreate(LONG_ARRAY_TAG_INIT_TARGET, longArray);
            }
            case NbtValue.LongLiteral(long value) -> callLongTag_valueOf(cw, new BytecodeValue.LongValue(value));
            case NbtValue.ShortLiteral(short value) -> callShortTag_valueOf(cw, new BytecodeValue.IntegerValue(value));
            case NbtValue.StringLiteral(String value) -> callStringTag_valueOf(cw, new BytecodeValue.StringValue(value));
            case null, default -> throw new IllegalArgumentException("Invalid nbt value: " + input);
        };
    }

    private static BytecodeValue writeTextValue(Text text, CodeWriter cw, CodeFieldStorage cf) {
        BytecodeValue textComponent;
        switch(text.content()) {
            case TextContent.LiteralText(String value) -> textComponent = callComponent_literal(cw, new BytecodeValue.StringValue(value));
            case TextContent.KeybindText(String keybind1) -> textComponent = callComponent_keybind(cw, new BytecodeValue.StringValue(keybind1));
            case TextContent.TranslatableText(String key, String fallback, List<Object> values) -> {
                if(values.isEmpty()) {
                    if(fallback == null) {
                        textComponent = callComponent_translatableEmpty(cw, new BytecodeValue.StringValue(key));
                    }else {
                        textComponent = callComponent_translatableFallbackEmpty(cw, new BytecodeValue.StringValue(key), new BytecodeValue.StringValue(fallback));
                    }
                }else {
                    var array = cw.newArray(new BytecodeValue.IntegerValue(values.size()), ConstantDescs.CD_Object);
                    for(int i = 0; i < values.size(); i++) {
                        BytecodeValue writeValue;
                        if(values.get(i) instanceof String str) {
                            writeValue = new BytecodeValue.StringValue(str);
                        }else if(values.get(i) instanceof Text t) {
                            writeValue = writeTextValue(t, cw, cf);
                        }else {
                            throw new IllegalArgumentException("Cannot write " + values.get(i) + " in 'translatable' args");
                        }
                        cw.setArrayValue(array, new BytecodeValue.IntegerValue(i), writeValue);
                    }
                    if(fallback == null) {
                        textComponent = callComponent_translatableArguments(cw, new BytecodeValue.StringValue(key), array);
                    }else {
                        textComponent = callComponent_translatableFallbackArguments(cw, new BytecodeValue.StringValue(key), new BytecodeValue.StringValue(fallback), array);
                    }
                }
            }
            case null, default -> throw new IllegalArgumentException("Invalid text content: " + text.content());
        }
        if(!text.style().isEmpty()) {
            textComponent = callMutableComponent_withStyle(cw, textComponent, cf.loadTextStyle(cw, text.style()));
        }
        for(var child : text.siblings()) {
            textComponent = callMutableComponent_append(cw, textComponent, writeTextValue(child, cw, cf));
        }
        return textComponent;
    }
}
