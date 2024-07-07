package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.EMIT_SERVER_FUNCTION_MANAGER_NAME;

public final class CodeFieldStorage {

    private final Class<?> serverFunctionManagerClass;
    private final Class<?> minecraftServerClass;
    private final CodeFieldStorageStorageMap map;

    public CodeFieldStorage(
            Class<?> serverFunctionManagerClass,
            Class<?> minecraftServerClass,
            CodeFieldStorageMap map
    ) {
        this.serverFunctionManagerClass = serverFunctionManagerClass;
        this.minecraftServerClass = minecraftServerClass;
        map.checkEntryCount();
        this.map = map.toStorageMap();
    }


    private final ArrayList<UUID> uuids = new ArrayList<>();

    private String getAttributeName(DataLocation value) {
        return DEBUG
                ? "attribute$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "a" + map.checkIndex(CodeFieldStorageMapKind.ATTRIBUTE, value);
    }

    private String getEffectName(DataLocation value) {
        return DEBUG
                ? "effect$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "e" + map.checkIndex(CodeFieldStorageMapKind.EFFECT, value);
    }

    private String getFunctionName(DataLocation value) {
        return DEBUG
                ? "function$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "f" + map.checkIndex(CodeFieldStorageMapKind.FUNCTION, value);
    }

    private String getResourceLocationName(DataLocation value) {
        return DEBUG
                ? "resourceLocation$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "r" + map.checkIndex(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
    }

    private String getUuidName(UUID value) {
        return DEBUG
                ? "uuid$" + value.toString().replace("-", "")
                : "u" + uuids.indexOf(value);
    }

    public void loadAttribute(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ATTRIBUTE, value);
        cw.getDataField(getAttributeName(value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE));
    }

    public void loadEffect(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.EFFECT, value);
        cw.getDataField(getEffectName(value), map.getValueClass(CodeFieldStorageMapKind.EFFECT));
    }

    public void loadFunction(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.FUNCTION, value);
        cw.getDataField(getFunctionName(value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION));
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        cw.getDataField(getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public void loadUUID(CodeWriter cw, UUID value) {
        if(!uuids.contains(value)) uuids.add(value);
        cw.getDataField(getUuidName(value), UUID.class);
    }

    public void loadServerFunctionManager(CodeWriter cw) {
        cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass);
    }

    public void generate(ClassWriter classWriter) {
        var mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "load", "(" + minecraftServerClass.descriptorString() + serverFunctionManagerClass.descriptorString() + ")V", null, null);
        mv.visitCode();
        var cw = new CodeWriter(mv);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        cw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass);

        for(var value : uuids) {
            cw.newObjectCreate(UUID.class);
            cw.writeLong(value.getMostSignificantBits());
            cw.writeLong(value.getLeastSignificantBits());
            cw.newObjectInit(UUID.class, long.class, long.class);
            cw.setDataField(getUuidName(value), UUID.class);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.RESOURCE_LOCATION)) {
            map.writeCode(CodeFieldStorageMapKind.RESOURCE_LOCATION, this, cw, value);
            cw.setDataField(getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.FUNCTION)) {
            map.writeCode(CodeFieldStorageMapKind.FUNCTION, this, cw, value);
            cw.setDataField(getFunctionName(value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION));
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.EFFECT)) {
            map.writeCode(CodeFieldStorageMapKind.EFFECT, this, cw, value);
            cw.setDataField(getEffectName(value), map.getValueClass(CodeFieldStorageMapKind.EFFECT));
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.ATTRIBUTE)) {
            map.writeCode(CodeFieldStorageMapKind.ATTRIBUTE, this, cw, value);
            cw.setDataField(getAttributeName(value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE));
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null);
        for(var value : uuids) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getUuidName(value), UUID.class.descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.RESOURCE_LOCATION)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.FUNCTION)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getFunctionName(value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.EFFECT)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getEffectName(value), map.getValueClass(CodeFieldStorageMapKind.EFFECT).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.ATTRIBUTE)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getAttributeName(value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE).descriptorString(), null, null);
        }

    }

}
