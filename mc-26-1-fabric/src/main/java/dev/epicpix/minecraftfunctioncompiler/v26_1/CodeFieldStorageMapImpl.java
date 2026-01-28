package dev.epicpix.minecraftfunctioncompiler.v26_1;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.*;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.*;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.*;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl().addDefaults();

    private static final InstructionTarget STYLE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(Style.class, TextColor.class, Integer.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, ClickEvent.class, HoverEvent.class, String.class, Identifier.class);
    private static final InstructionTarget LIST_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ListTag.class);
    private static final InstructionTarget BYTE_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ByteArrayTag.class, byte[].class);
    private static final InstructionTarget COMPOUND_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompoundTag.class);
    private static final InstructionTarget INT_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(IntArrayTag.class, int[].class);
    private static final InstructionTarget LONG_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(LongArrayTag.class, long[].class);

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ADVANCEMENT, new CodeFieldStorageMapValue<>(Type.getType(AdvancementHolder.class), (cf, cw, value) -> {
            return callServerAdvancementManager_get(cw, callMinecraftServer_getAdvancements(cw, cf.getGeneratorMinecraftServer()), cf.loadIdentifier(cw, value));
        }));
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Type.getType(Holder.class), (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_get(cw, loadBuiltInRegistries_ATTRIBUTE(cw), cf.loadIdentifier(cw, value))), Type.getType(Holder.class));
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(Type.getType(Holder.class), (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_get(cw, loadBuiltInRegistries_MOB_EFFECT(cw), cf.loadIdentifier(cw, value))), Type.getType(Holder.class));
        }));
        set(CodeFieldStorageMapKind.ENTITY_TYPE, new CodeFieldStorageMapValue<>(Type.getType(EntityType.class), (cf, cw, value) -> {
            return cw.castType(callRegistry_getValue(cw, loadBuiltInRegistries_ENTITY_TYPE(cw), cf.loadIdentifier(cw, value)), Type.getType(EntityType.class));
        }));
        set(CodeFieldStorageMapKind.FUNCTION, new CodeFieldStorageMapValue<>(Type.getType(CommandFunction.class), (cf, cw, value) -> {
            var functions = loadServerFunctionLibrary_functions(cw, loadServerFunctionManager_library(cw, cf.loadServerFunctionManager(cw)));
            return cw.castType(callMap_get(cw, functions, cf.loadIdentifier(cw, value)), Type.getType(CommandFunction.class));
        }));
        set(CodeFieldStorageMapKind.NBT_COMPOUND, new CodeFieldStorageMapValue<>(Type.getType(CompoundTag.class), (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.NBT_VALUE, new CodeFieldStorageMapValue<>(Type.getType(Tag.class), (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.PREDICATE, new CodeFieldStorageMapValue<>(Type.getType(LootItemCondition.class), (cf, cw, value) -> {
            var resourceKey = callResourceKey_create(cw, loadRegistries_PREDICATE(cw), cf.loadIdentifier(cw, value));
            return cw.castType(callHolder_value(cw, callOptional_get(cw, callHolderLookupProvider_get(cw, callReloadableServerRegistriesHolder_lookup(cw, callMinecraftServer_reloadableRegistries(cw, cf.getGeneratorMinecraftServer())), resourceKey))), Type.getType(LootItemCondition.class));
        }));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(Type.getType(Identifier.class), (cf, cw, value) -> {
            return callIdentifier_fromNamespaceAndPath(cw, new BytecodeValue.StringValue(value.namespace()), new BytecodeValue.StringValue(value.path()));
        }));
        set(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, new CodeFieldStorageMapValue<>(Type.getType(ObjectiveCriteria.class), (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callObjectiveCriteria_byName(cw, new BytecodeValue.StringValue(value))), Type.getType(ObjectiveCriteria.class));
        }));
        set(CodeFieldStorageMapKind.TEXT, new CodeFieldStorageMapValue<>(Type.getType(Component.class), (cf, cw, value) -> writeTextValue(value, cw, cf)));
        set(CodeFieldStorageMapKind.TEXT_STYLE, new CodeFieldStorageMapValue<>(Type.getType(Style.class), (cf, cw, value) -> {
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
            args.add(new BytecodeValue.NullValue());
            args.add(new BytecodeValue.NullValue());
            if(value.insertion() != null) {
                args.add(new BytecodeValue.StringValue(value.insertion()));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            if(value.font() != null) {
                args.add(cf.loadIdentifier(cw, value.font()));
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
                var byteArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), Type.getType(byte.class));
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
                var intArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), Type.getType(int.class));
                for(int i = 0; i < values.length; i++) {
                    cw.setArrayValue(intArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.IntegerValue(values[i]));
                }
                yield cw.newObjectCreate(INT_ARRAY_TAG_INIT_TARGET, intArray);
            }
            case NbtValue.IntegerLiteral(int value) -> callIntTag_valueOf(cw, new BytecodeValue.IntegerValue(value));
            case NbtValue.LongArray(long[] values) -> {
                var longArray = cw.newArray(new BytecodeValue.IntegerValue(values.length), Type.getType(long.class));
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
                    var array = cw.newArray(new BytecodeValue.IntegerValue(values.size()), Type.getType(Object.class));
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
