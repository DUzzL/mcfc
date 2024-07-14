package dev.epicpix.minecraftfunctioncompiler.v1_21;

import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMap;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMap_get;
import static dev.epicpix.minecraftfunctioncompiler.v1_21.emitter.MappedUsageDefinitionsExtensions.*;

public final class CodeFieldStorageMapImpl extends CodeFieldStorageMap {
    public static final CodeFieldStorageMap INSTANCE = new CodeFieldStorageMapImpl();

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
        set(CodeFieldStorageMapKind.NBT_VALUE, new CodeFieldStorageMapValue<>(Tag.class, (cf, cw, value) -> writeNbtValue(value, cw)));
        set(CodeFieldStorageMapKind.RESOURCE_LOCATION, new CodeFieldStorageMapValue<>(ResourceLocation.class, (cf, cw, value) -> {
            cw.writeString(value.namespace());
            cw.writeString(value.path());
            callResourceLocation_fromNamespaceAndPath(cw);
            return 9;
        }));
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
}
