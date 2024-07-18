package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.*;

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

    private String getNbtValueName(NbtValue value) {
        return "n" + map.checkIndex(CodeFieldStorageMapKind.NBT_VALUE, value);
    }

    private String getResourceLocationName(DataLocation value) {
        return DEBUG
                ? "resourceLocation$" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "r" + map.checkIndex(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
    }

    private String getScoreboardCriterionName(String value) {
        return DEBUG
                ? "scoreboardCriterion$" + value.replace(':', '$').replace('.', '$')
                : "s" + map.checkIndex(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
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

    public void loadNbtValue(CodeWriter cw, NbtValue value) {
        map.writeValue(CodeFieldStorageMapKind.NBT_VALUE, value);
        cw.getDataField(getNbtValueName(value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE));
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        cw.getDataField(getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public void loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        cw.getDataField(getScoreboardCriterionName(value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
    }

    public void loadUUID(CodeWriter cw, UUID value) {
        if(!uuids.contains(value)) uuids.add(value);
        cw.getDataField(getUuidName(value), UUID.class);
    }

    public void loadServerFunctionManager(CodeWriter cw) {
        cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass);
    }

    private int currentFunctionDefinition;
    private int cost;

    private CodeWriter getCurrentFunction(CodeWriter cw, ClassWriter classWriter) {
        if(cost >= 60000) {
            currentFunctionDefinition++;
            cost = 0;
            cw.methodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, DATA_CLASS, "load" + currentFunctionDefinition, "()V", false);
            cw.methodVisitor().visitInsn(Opcodes.RETURN);
            cw.methodVisitor().visitMaxs(0, 0);
            cw.methodVisitor().visitEnd();
            var mv = classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "load" + currentFunctionDefinition, "()V", null, null);
            mv.visitCode();
            return new CodeWriter(mv);
        }
        return cw;
    }

    public void generate(ClassWriter classWriter) {
        currentFunctionDefinition = 1;
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
            cost += 16;
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.RESOURCE_LOCATION)) {
            cost += map.writeCode(CodeFieldStorageMapKind.RESOURCE_LOCATION, this, cw, value) + 3;
            cw.setDataField(getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.FUNCTION)) {
            cost += map.writeCode(CodeFieldStorageMapKind.FUNCTION, this, cw, value) + 3;
            cw.setDataField(getFunctionName(value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.EFFECT)) {
            cost += map.writeCode(CodeFieldStorageMapKind.EFFECT, this, cw, value) + 3;
            cw.setDataField(getEffectName(value), map.getValueClass(CodeFieldStorageMapKind.EFFECT));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.ATTRIBUTE)) {
            cost += map.writeCode(CodeFieldStorageMapKind.ATTRIBUTE, this, cw, value) + 3;
            cw.setDataField(getAttributeName(value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.SCOREBOARD_CRITERION)) {
            cost += map.writeCode(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, this, cw, value) + 3;
            cw.setDataField(getScoreboardCriterionName(value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.NBT_VALUE)) {
            cost += map.writeCode(CodeFieldStorageMapKind.NBT_VALUE, this, cw, value) + 3;
            cw.setDataField(getNbtValueName(value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE));
            cw = getCurrentFunction(cw, classWriter);
        }

        cw.methodVisitor().visitInsn(Opcodes.RETURN);
        cw.methodVisitor().visitMaxs(0, 0);
        cw.methodVisitor().visitEnd();

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
        for(var value : map.getStorage(CodeFieldStorageMapKind.SCOREBOARD_CRITERION)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getScoreboardCriterionName(value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.NBT_VALUE)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getNbtValueName(value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE).descriptorString(), null, null);
        }

    }

}
