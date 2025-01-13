package dev.epicpix.minecraftfunctioncompiler.v1_20_6;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.*;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.*;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.*;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl().addDefaults();

    private static final InstructionTarget RESOURCE_LOCATION_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ResourceLocation.class, String.class, String.class);
    private static final InstructionTarget STYLE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(Style.class, TextColor.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, ClickEvent.class, HoverEvent.class, String.class, ResourceLocation.class);
    private static final InstructionTarget LIST_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ListTag.class);
    private static final InstructionTarget BYTE_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(ByteArrayTag.class, byte[].class);
    private static final InstructionTarget COMPOUND_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompoundTag.class);
    private static final InstructionTarget INT_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(IntArrayTag.class, int[].class);
    private static final InstructionTarget LONG_ARRAY_TAG_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(LongArrayTag.class, long[].class);

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Type.getType(Holder.class), (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_getHolder(cw, loadBuiltInRegistries_ATTRIBUTE(cw), cf.loadResourceLocation(cw, value))), Type.getType(Holder.class));
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(Type.getType(Holder.class), (cf, cw, value) -> {
            return cw.castType(callOptional_get(cw, callRegistry_getHolder(cw, loadBuiltInRegistries_MOB_EFFECT(cw), cf.loadResourceLocation(cw, value))), Type.getType(Holder.class));
        }));
        set(CodeFieldStorageMapKind.FUNCTION, new CodeFieldStorageMapValue<>(Type.getType(CommandFunction.class), (cf, cw, value) -> {
            var functions = loadServerFunctionLibrary_functions(cw, loadServerFunctionManager_library(cw, cf.loadServerFunctionManager(cw)));
            return cw.castType(callMap_get(cw, functions, cf.loadResourceLocation(cw, value)), Type.getType(CommandFunction.class));
        }));
        set(CodeFieldStorageMapKind.NBT_COMPOUND, new CodeFieldStorageMapValue<>(Type.getType(CompoundTag.class), (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.NBT_VALUE, new CodeFieldStorageMapValue<>(Type.getType(Tag.class), (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(Type.getType(ResourceLocation.class), (cf, cw, value) -> {
            return cw.newObjectCreate(RESOURCE_LOCATION_INIT_TARGET, new BytecodeValue.StringValue(value.namespace()), new BytecodeValue.StringValue(value.path()));
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
                args.add(cf.loadResourceLocation(cw, value.font()));
            }else {
                args.add(new BytecodeValue.NullValue());
            }
            return cw.newObjectCreate(STYLE_INIT_TARGET, args);
        }));
    }

    private static BytecodeValue writeBoolean(CodeWriter cw, Boolean value) {
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

    private static BytecodeValue writeNbtValue(NbtValue value, CodeWriter cw) {
        if(value instanceof NbtValue.Array arr) {
            var listTag = cw.newObjectCreate(LIST_TAG_INIT_TARGET);
            List<NbtValue> values = arr.values();
            for(int i = 0; i < values.size(); i++) {
                callCollectionTag_add(cw, listTag, new BytecodeValue.IntegerValue(i), writeNbtValue(values.get(i), cw));
            }
            return listTag;
        }else if(value instanceof NbtValue.ByteArray barr) {
            var byteArray = cw.newArray(new BytecodeValue.IntegerValue(barr.values().length), Type.getType(byte.class));
            byte[] values = barr.values();
            for(int i = 0; i < values.length; i++) {
                cw.setArrayValue(byteArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.IntegerValue(values[i]));
            }
            return cw.newObjectCreate(BYTE_ARRAY_TAG_INIT_TARGET, byteArray);
        }else if(value instanceof NbtValue.ByteLiteral bl) {
            return callByteTag_valueOf(cw, new BytecodeValue.IntegerValue(bl.value()));
        }else if(value instanceof NbtValue.Compound comp) {
            var compoundTag = cw.newObjectCreate(COMPOUND_TAG_INIT_TARGET);
            for(var entry : comp.values().entrySet()) {
                callCompoundTag_put(cw, compoundTag, new BytecodeValue.StringValue(entry.getKey()), writeNbtValue(entry.getValue(), cw));
            }
            return compoundTag;
        }else if(value instanceof NbtValue.DoubleLiteral dl) {
            return callDoubleTag_valueOf(cw, new BytecodeValue.DoubleValue(dl.value()));
        }else if(value instanceof NbtValue.FloatLiteral fl) {
            return callFloatTag_valueOf(cw, new BytecodeValue.FloatValue(fl.value()));
        }else if(value instanceof NbtValue.IntegerArray iarr) {
            var intArray = cw.newArray(new BytecodeValue.IntegerValue(iarr.values().length), Type.getType(int.class));
            int[] values = iarr.values();
            for(int i = 0; i < values.length; i++) {
                cw.setArrayValue(intArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.IntegerValue(values[i]));
            }
            return cw.newObjectCreate(INT_ARRAY_TAG_INIT_TARGET, intArray);
        }else if(value instanceof NbtValue.IntegerLiteral il) {
            return callIntTag_valueOf(cw, new BytecodeValue.IntegerValue(il.value()));
        }else if(value instanceof NbtValue.LongArray larr) {
            var longArray = cw.newArray(new BytecodeValue.IntegerValue(larr.values().length), Type.getType(long.class));
            long[] values = larr.values();
            for(int i = 0; i < values.length; i++) {
                cw.setArrayValue(longArray, new BytecodeValue.IntegerValue(i), new BytecodeValue.LongValue(values[i]));
            }
            return cw.newObjectCreate(LONG_ARRAY_TAG_INIT_TARGET, longArray);
        }else if(value instanceof NbtValue.LongLiteral ll) {
            return callLongTag_valueOf(cw, new BytecodeValue.LongValue(ll.value()));
        }else if(value instanceof NbtValue.ShortLiteral sl) {
            return callShortTag_valueOf(cw, new BytecodeValue.IntegerValue(sl.value()));
        }else if(value instanceof NbtValue.StringLiteral sl) {
            return callStringTag_valueOf(cw, new BytecodeValue.StringValue(sl.value()));
        }else {
            throw new IllegalArgumentException("Invalid nbt value: " + value);
        }
    }

    private static BytecodeValue writeTextValue(Text text, CodeWriter cw, CodeFieldStorage cf) {
        BytecodeValue textComponent;
        if(text.content() instanceof TextContent.LiteralText literal) {
            textComponent = callComponent_literal(cw, new BytecodeValue.StringValue(literal.value()));
        }else if(text.content() instanceof TextContent.KeybindText keybind) {
            textComponent = callComponent_keybind(cw, new BytecodeValue.StringValue(keybind.keybind()));
        }else if(text.content() instanceof TextContent.TranslatableText translatable) {
            if(translatable.args().isEmpty()) {
                if(translatable.fallback() == null) {
                    textComponent = callComponent_translatableEmpty(cw, new BytecodeValue.StringValue(translatable.key()));
                }else {
                    textComponent = callComponent_translatableFallbackEmpty(cw, new BytecodeValue.StringValue(translatable.key()), new BytecodeValue.StringValue(translatable.fallback()));
                }
            }else {
                var array = cw.newArray(new BytecodeValue.IntegerValue(translatable.args().size()), Type.getType(Object.class));
                var values = translatable.args();
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
                if(translatable.fallback() == null) {
                    textComponent = callComponent_translatableArguments(cw, new BytecodeValue.StringValue(translatable.key()), array);
                }else {
                    textComponent = callComponent_translatableFallbackArguments(cw, new BytecodeValue.StringValue(translatable.key()), new BytecodeValue.StringValue(translatable.fallback()), array);
                }
            }
        }else {
            throw new IllegalArgumentException("Invalid text content: " + text.content());
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
