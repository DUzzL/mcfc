package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.EMIT_SERVER_FUNCTION_MANAGER_NAME;

public final class CodeFieldStorage {

    private final Class<?> serverFunctionManagerClass;
    private final Class<?> minecraftServerClass;
    private final EnumMap<CodeFieldStorageMapEnum, CodeFieldStorageMapValue> enumMap;

    public CodeFieldStorage(
            Class<?> serverFunctionManagerClass,
            Class<?> minecraftServerClass,
            EnumMap<CodeFieldStorageMapEnum, CodeFieldStorageMapValue> enumMap
    ) {
        this.serverFunctionManagerClass = serverFunctionManagerClass;
        this.minecraftServerClass = minecraftServerClass;
        this.enumMap = enumMap.clone();
        if(this.enumMap.size() != CodeFieldStorageMapEnum.values().length) {
            throw new IllegalArgumentException("EnumMap does not map every enum value");
        }
    }


    private final ArrayList<UUID> uuids = new ArrayList<>();
    private final ArrayList<DataLocation> resourceLocations = new ArrayList<>();
    private final ArrayList<DataLocation> functions = new ArrayList<>();
    private final ArrayList<DataLocation> attributes = new ArrayList<>();

    private String getUuidName(UUID value) {
        return DEBUG
                ? "uuid$" + value.toString().replace("-", "")
                : "u" + uuids.indexOf(value);
    }

    private String getResourceLocationName(DataLocation value) {
        return DEBUG
                ? "resourceLocation$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "r" + resourceLocations.indexOf(value);
    }

    private String getFunctionName(DataLocation value) {
        return DEBUG
                ? "function$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "f" + functions.indexOf(value);
    }

    private String getAttributeName(DataLocation value) {
        return DEBUG
                ? "attribute$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "a" + attributes.indexOf(value);
    }

    public void loadUUID(CodeWriter cw, UUID value) {
        if(!uuids.contains(value)) uuids.add(value);
        cw.getDataField(getUuidName(value), UUID.class);
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value);
        cw.getDataField(getResourceLocationName(value), enumMap.get(CodeFieldStorageMapEnum.RESOURCE_LOCATION).clazz());
    }

    public void loadFunction(CodeWriter cw, DataLocation value) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value);
        if(!functions.contains(value)) functions.add(value);
        cw.getDataField(getFunctionName(value), enumMap.get(CodeFieldStorageMapEnum.FUNCTION).clazz());
    }

    public void loadAttribute(CodeWriter cw, DataLocation value) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value);
        if(!attributes.contains(value)) attributes.add(value);
        cw.getDataField(getAttributeName(value), enumMap.get(CodeFieldStorageMapEnum.ATTRIBUTE).clazz());
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

        for(var value : resourceLocations) {
            enumMap.get(CodeFieldStorageMapEnum.RESOURCE_LOCATION).mapper().accept(this, cw, value);
            cw.setDataField(getResourceLocationName(value), enumMap.get(CodeFieldStorageMapEnum.RESOURCE_LOCATION).clazz());
        }

        for(var value : functions) {
            enumMap.get(CodeFieldStorageMapEnum.FUNCTION).mapper().accept(this, cw, value);
            cw.setDataField(getFunctionName(value), enumMap.get(CodeFieldStorageMapEnum.FUNCTION).clazz());
        }

        for(var value : attributes) {
            enumMap.get(CodeFieldStorageMapEnum.ATTRIBUTE).mapper().accept(this, cw, value);
            cw.setDataField(getAttributeName(value), enumMap.get(CodeFieldStorageMapEnum.ATTRIBUTE).clazz());
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null);
        for(var value : uuids) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getUuidName(value), UUID.class.descriptorString(), null, null);
        }
        for(var value : resourceLocations) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getResourceLocationName(value), enumMap.get(CodeFieldStorageMapEnum.RESOURCE_LOCATION).clazz().descriptorString(), null, null);
        }
        for(var value : functions) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getFunctionName(value), enumMap.get(CodeFieldStorageMapEnum.FUNCTION).clazz().descriptorString(), null, null);
        }
        for(var value : attributes) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getAttributeName(value), enumMap.get(CodeFieldStorageMapEnum.ATTRIBUTE).clazz().descriptorString(), null, null);
        }

    }

}
