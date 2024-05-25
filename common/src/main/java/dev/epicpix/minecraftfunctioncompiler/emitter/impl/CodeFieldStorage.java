package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.util.TriConsumer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.EMIT_SERVER_FUNCTION_MANAGER_NAME;

public final class CodeFieldStorage {

    private final Class<?> resourceLocationClass;
    private final Class<?> commandFunctionClass;
    private final Class<?> serverFunctionManagerClass;
    private final Class<?> minecraftServerClass;
    private final TriConsumer<CodeFieldStorage, CodeWriter, DataLocation> mapFunction;

    public CodeFieldStorage(Class<?> resourceLocationClass, Class<?> commandFunctionClass, Class<?> serverFunctionManagerClass, Class<?> minecraftServerClass, TriConsumer<CodeFieldStorage, CodeWriter, DataLocation> mapFunction) {
        this.resourceLocationClass = resourceLocationClass;
        this.commandFunctionClass = commandFunctionClass;
        this.serverFunctionManagerClass = serverFunctionManagerClass;
        this.minecraftServerClass = minecraftServerClass;
        this.mapFunction = mapFunction;
    }


    private final ArrayList<UUID> uuids = new ArrayList<>();
    private final ArrayList<DataLocation> resourceLocations = new ArrayList<>();
    private final ArrayList<DataLocation> functions = new ArrayList<>();

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
                : "r" + functions.indexOf(value);
    }

    public void loadUUID(CodeWriter cw, UUID value) {
        if(!uuids.contains(value)) uuids.add(value);
        cw.getDataField(getUuidName(value), UUID.class);
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value);
        cw.getDataField(getResourceLocationName(value), resourceLocationClass);
    }

    public void loadFunction(CodeWriter cw, DataLocation value) {
        if(!resourceLocations.contains(value)) resourceLocations.add(value);
        if(!functions.contains(value)) functions.add(value);
        cw.getDataField(getFunctionName(value), commandFunctionClass);
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
            cw.newObjectCreate(resourceLocationClass);
            cw.writeString(value.namespace());
            cw.writeString(value.path());
            cw.newObjectInit(resourceLocationClass, String.class, String.class);
            cw.setDataField(getResourceLocationName(value), resourceLocationClass);
        }

        for(var value : functions) {
            mapFunction.accept(this, cw, value);
            cw.setDataField(getFunctionName(value), commandFunctionClass);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null);
        for(var value : uuids) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getUuidName(value), UUID.class.descriptorString(), null, null);
        }
        for(var value : resourceLocations) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getResourceLocationName(value), resourceLocationClass.descriptorString(), null, null);
        }
        for(var value : functions) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getFunctionName(value), commandFunctionClass.descriptorString(), null, null);
        }

    }

}
