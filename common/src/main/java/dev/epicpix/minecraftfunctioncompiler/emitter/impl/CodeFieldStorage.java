package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.*;

public final class CodeFieldStorage {

    public final Class<?> serverFunctionManagerClass;
    public final Class<?> minecraftServerClass;
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

    public CodeFieldStorageStorageMap getMap() {
        return map;
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

    private String getRegexPatternName(String value) {
        return DEBUG
                ? "regex" + value.toString().replace('/', '$').replace(':', '$').replace('.', '$')
                : "R" + map.checkIndex(CodeFieldStorageMapKind.REGEX_PATTERN, value);
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

    private String getTextName(Text value) {
        return "t" + map.checkIndex(CodeFieldStorageMapKind.TEXT, value);
    }

    private String getTextStyleName(TextStyle value) {
        return "ts" + map.checkIndex(CodeFieldStorageMapKind.TEXT_STYLE, value);
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

    public void loadRegexPattern(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.REGEX_PATTERN, value);
        cw.getDataField(getRegexPatternName(value), map.getValueClass(CodeFieldStorageMapKind.REGEX_PATTERN));
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        cw.getDataField(getResourceLocationName(value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public void loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        cw.getDataField(getScoreboardCriterionName(value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
    }

    public void loadText(CodeWriter cw, Text value) {
        value.forEachComponent(text -> {
            if(!text.style().isEmpty()) {
                map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, text.style());
                if (text.style().font() != null) {
                    map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, text.style().font());
                }
            }
        });
        map.writeValue(CodeFieldStorageMapKind.TEXT, value);
        cw.getDataField(getTextName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT));
    }

    public void loadTextStyle(CodeWriter cw, TextStyle value) {
        map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, value);
        if(value.font() != null) {
            map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value.font());
        }
        cw.getDataField(getTextStyleName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT_STYLE));
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

        for(var value : map.getStorage(CodeFieldStorageMapKind.REGEX_PATTERN)) {
            cost += map.writeCode(CodeFieldStorageMapKind.REGEX_PATTERN, this, cw, value) + 3;
            cw.setDataField(getRegexPatternName(value), map.getValueClass(CodeFieldStorageMapKind.REGEX_PATTERN));
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

        for(var value : map.getStorage(CodeFieldStorageMapKind.TEXT_STYLE)) {
            cost += map.writeCode(CodeFieldStorageMapKind.TEXT_STYLE, this, cw, value) + 3;
            cw.setDataField(getTextStyleName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT_STYLE));
            cw = getCurrentFunction(cw, classWriter);
        }

        for(var value : map.getStorage(CodeFieldStorageMapKind.TEXT)) {
            cost += map.writeCode(CodeFieldStorageMapKind.TEXT, this, cw, value) + 3;
            cw.setDataField(getTextName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT));
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
        for(var value : map.getStorage(CodeFieldStorageMapKind.TEXT_STYLE)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getTextStyleName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT_STYLE).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.TEXT)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getTextName(value), map.getValueClass(CodeFieldStorageMapKind.TEXT).descriptorString(), null, null);
        }
        for(var value : map.getStorage(CodeFieldStorageMapKind.NBT_VALUE)) {
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, getNbtValueName(value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE).descriptorString(), null, null);
        }

    }

}
