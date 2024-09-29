package dev.epicpix.minecraftfunctioncompiler.v1_21;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.*;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.regex.Pattern;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.*;
import static dev.epicpix.minecraftfunctioncompiler.v1_21.emitter.MappedUsageDefinitionsExtensions.*;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl().addDefaults();

    CodeFieldStorageMapImpl() {
        set(CodeFieldStorageMapKind.ATTRIBUTE, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            loadBuiltInRegistries_ATTRIBUTE(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_getHolder(cw);
            callOptional_get(cw);
            cw.castType(Holder.class);
            return 15;
        }));
        set(CodeFieldStorageMapKind.EFFECT, new CodeFieldStorageMapValue<>(Holder.class, (cf, cw, value) -> {
            loadBuiltInRegistries_MOB_EFFECT(cw);
            cf.loadResourceLocation(cw, value);
            callRegistry_getHolder(cw);
            callOptional_get(cw);
            cw.castType(Holder.class);
            return 15;
        }));
        set(CodeFieldStorageMapKind.FUNCTION, new CodeFieldStorageMapValue<>(CommandFunction.class, (cf, cw, value) -> {
            cf.loadServerFunctionManager(cw);
            loadServerFunctionManager_library(cw);
            loadServerFunctionLibrary_functions(cw);
            cf.loadResourceLocation(cw, value);
            callMap_get(cw);
            cw.castType(CommandFunction.class);
            return 18;
        }));
        set(CodeFieldStorageMapKind.NBT_COMPOUND, new CodeFieldStorageMapValue<>(CompoundTag.class, (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.NBT_VALUE, new CodeFieldStorageMapValue<>(Tag.class, (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(ResourceLocation.class, (cf, cw, value) -> {
            cw.writeString(value.namespace());
            cw.writeString(value.path());
            callResourceLocation_fromNamespaceAndPath(cw);
            return 9;
        }));
        set(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, new CodeFieldStorageMapValue<>(ObjectiveCriteria.class, (cf, cw, value) -> {
            cw.writeString(value);
            callObjectiveCriteria_byName(cw);
            callOptional_get(cw);
            cw.castType(ObjectiveCriteria.class);
            return 12;
        }));
        set(CodeFieldStorageMapKind.TEXT, new CodeFieldStorageMapValue<>(Component.class, (cf, cw, value) -> writeTextValue(value, cw, cf)));
        set(CodeFieldStorageMapKind.TEXT_STYLE, new CodeFieldStorageMapValue<>(Style.class, (cf, cw, value) -> {
            cw.newObjectCreate(Style.class);
            int cost = 8;
            if(value.color() != null) {
                cw.writeInt(value.color());
                callTextColor_fromRgb(cw);
                cost += 6;
            }else {
                cw.writeNull();
                cost++;
            }
            cost += writeBoolean(cw, value.bold());
            cost += writeBoolean(cw, value.italic());
            cost += writeBoolean(cw, value.underlined());
            cost += writeBoolean(cw, value.strikethrough());
            cost += writeBoolean(cw, value.obfuscated());
            cw.writeNull();
            cw.writeNull();
            if(value.insertion() != null) {
                cw.writeString(value.insertion());
                cost += 3;
            }else {
                cw.writeNull();
                cost++;
            }
            if(value.font() != null) {
                cf.loadResourceLocation(cw, value.font());
                cost += 3;
            }else {
                cw.writeNull();
                cost++;
            }
            cw.newObjectInit(Style.class, TextColor.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, ClickEvent.class, HoverEvent.class, String.class, ResourceLocation.class);
            return cost;
        }));
    }

    private static int writeBoolean(CodeWriter cw, Boolean value) {
        if(value == null) {
            cw.writeNull();
            return 1;
        }else {
            if(value) {
                loadBoolean_TRUE(cw);
            }else {
                loadBoolean_FALSE(cw);
            }
            return 3;
        }
    }

    private static int writeNbtValue(NbtValue value, CodeWriter cw) {
        if(value instanceof NbtValue.Array arr) {
            cw.newObjectCreate(ListTag.class);
            cw.newObjectInit(ListTag.class);
            List<NbtValue> values = arr.values();
            int cost = 7;
            for(int i = 0; i < values.size(); i++) {
                cw.methodVisitor().visitInsn(Opcodes.DUP);
                cw.writeInt(i);
                cost += writeNbtValue(values.get(i), cw) + 7;
                callCollectionTag_add(cw);
            }
            return cost;
        }else if(value instanceof NbtValue.ByteArray barr) {
            cw.newObjectCreate(ByteArrayTag.class);
            cw.writeInt(barr.values().length);
            cw.methodVisitor().visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
            byte[] values = barr.values();
            for(int i = 0; i < values.length; i++) {
                cw.methodVisitor().visitInsn(Opcodes.DUP);
                cw.writeInt(i);
                cw.writeInt(values[i]);
                cw.methodVisitor().visitInsn(Opcodes.BASTORE);
            }
            cw.newObjectInit(ByteArrayTag.class, byte[].class);
            return 12 + values.length * 7;
        }else if(value instanceof NbtValue.ByteLiteral bl) {
            cw.writeInt(bl.value());
            callByteTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.Compound comp) {
            cw.newObjectCreate(CompoundTag.class);
            cw.newObjectInit(CompoundTag.class);
            int cost = 7;
            for(var entry : comp.values().entrySet()) {
                cw.methodVisitor().visitInsn(Opcodes.DUP);
                cw.writeString(entry.getKey());
                cost += writeNbtValue(entry.getValue(), cw) + 8;
                callCompoundTag_put(cw);
                cw.methodVisitor().visitInsn(Opcodes.POP);
            }
            return cost;
        }else if(value instanceof NbtValue.DoubleLiteral dl) {
            cw.writeDouble(dl.value());
            callDoubleTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.FloatLiteral fl) {
            cw.writeFloat(fl.value());
            callFloatTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.IntegerArray iarr) {
            cw.newObjectCreate(IntArrayTag.class);
            cw.writeInt(iarr.values().length);
            cw.methodVisitor().visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            int[] values = iarr.values();
            for(int i = 0; i < values.length; i++) {
                cw.methodVisitor().visitInsn(Opcodes.DUP);
                cw.writeInt(i);
                cw.writeInt(values[i]);
                cw.methodVisitor().visitInsn(Opcodes.IASTORE);
            }
            cw.newObjectInit(IntArrayTag.class, int[].class);
            return 12 + values.length * 7;
        }else if(value instanceof NbtValue.IntegerLiteral il) {
            cw.writeInt(il.value());
            callIntTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.LongArray larr) {
            cw.newObjectCreate(LongArrayTag.class);
            cw.writeInt(larr.values().length);
            cw.methodVisitor().visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG);
            long[] values = larr.values();
            for(int i = 0; i < values.length; i++) {
                cw.methodVisitor().visitInsn(Opcodes.DUP);
                cw.writeInt(i);
                cw.writeLong(values[i]);
                cw.methodVisitor().visitInsn(Opcodes.LASTORE);
            }
            cw.newObjectInit(LongArrayTag.class, long[].class);
            return 12 + values.length * 7;
        }else if(value instanceof NbtValue.LongLiteral ll) {
            cw.writeLong(ll.value());
            callLongTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.ShortLiteral sl) {
            cw.writeInt(sl.value());
            callShortTag_valueOf(cw);
            return 6;
        }else if(value instanceof NbtValue.StringLiteral sl) {
            cw.writeString(sl.value());
            callStringTag_valueOf(cw);
            return 6;
        }else {
            throw new IllegalArgumentException("Invalid nbt value: " + value);
        }
    }

    private static int writeTextValue(Text text, CodeWriter cw, CodeFieldStorage cf) {
        int cost = 6;
        if(text.content() instanceof TextContent.LiteralText literal) {
            cw.writeString(literal.value());
            callComponent_literal(cw);
            cost += 6;
        }else if(text.content() instanceof TextContent.KeybindText keybind) {
            cw.writeString(keybind.keybind());
            callComponent_keybind(cw);
            cost += 6;
        }else if(text.content() instanceof TextContent.TranslatableText translatable) {
            cw.writeString(translatable.key());
            if(translatable.fallback() != null) {
                cw.writeString(translatable.fallback());
                cost += 3;
            }
            if(translatable.args().isEmpty()) {
                if(translatable.fallback() == null) {
                    callComponent_translatableEmpty(cw);
                    cost += 3;
                }else {
                    callComponent_translatableFallbackEmpty(cw);
                    cost += 3;
                }
            }else {
                cw.writeInt(translatable.args().size());
                cw.methodVisitor().visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
                var values = translatable.args();
                for(int i = 0; i < values.size(); i++) {
                    cw.methodVisitor().visitInsn(Opcodes.DUP);
                    cw.writeInt(i);
                    if(values.get(i) instanceof String str) {
                        cw.writeString(str);
                        cost += 3;
                    }else if(values.get(i) instanceof Text t) {
                        cost += writeTextValue(t, cw, cf);
                    }else {
                        throw new IllegalArgumentException("Cannot write " + values.get(i) + " in 'translatable' args");
                    }
                    cw.methodVisitor().visitInsn(Opcodes.AASTORE);
                }
                cost += 6 + values.size() * 5;
                if(translatable.fallback() == null) {
                    callComponent_translatableArguments(cw);
                }else {
                    callComponent_translatableFallbackArguments(cw);
                }
                cost += 3;
            }
        }else {
            throw new IllegalArgumentException("Invalid text content: " + text.content());
        }
        if(!text.style().isEmpty()) {
            cf.loadTextStyle(cw, text.style());
            callMutableComponent_withStyle(cw);
        }
        for(var child : text.siblings()) {
            cost += writeTextValue(child, cw, cf) + 3;
            callMutableComponent_append(cw);
        }
        return cost;
    }
}
