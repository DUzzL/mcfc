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

    public void loadAttribute(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ATTRIBUTE, value);
        cw.getDataField(CodeFieldStorageMapKind.ATTRIBUTE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE));
    }

    public void loadEffect(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.EFFECT, value);
        cw.getDataField(CodeFieldStorageMapKind.EFFECT.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.EFFECT));
    }

    public void loadFunction(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.FUNCTION, value);
        cw.getDataField(CodeFieldStorageMapKind.FUNCTION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION));
    }

    public void loadNbtValue(CodeWriter cw, NbtValue value) {
        map.writeValue(CodeFieldStorageMapKind.NBT_VALUE, value);
        cw.getDataField(CodeFieldStorageMapKind.NBT_VALUE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE));
    }

    public void loadRegexPattern(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.REGEX_PATTERN, value);
        cw.getDataField(CodeFieldStorageMapKind.REGEX_PATTERN.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.REGEX_PATTERN));
    }

    public void loadResourceLocation(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        cw.getDataField(CodeFieldStorageMapKind.RESOURCE_LOCATION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public void loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        cw.getDataField(CodeFieldStorageMapKind.SCOREBOARD_CRITERION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
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
        cw.getDataField(CodeFieldStorageMapKind.TEXT.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.TEXT));
    }

    public void loadTextStyle(CodeWriter cw, TextStyle value) {
        map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, value);
        if(value.font() != null) {
            map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value.font());
        }
        cw.getDataField(CodeFieldStorageMapKind.TEXT_STYLE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.TEXT_STYLE));
    }

    public void loadUUID(CodeWriter cw, UUID value) {
        map.writeValue(CodeFieldStorageMapKind.UUID, value);
        cw.getDataField(CodeFieldStorageMapKind.UUID.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.UUID));
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

    private <T> CodeWriter writeFromStorage(HashSet<CodeFieldStorageMapKind<?>> written, CodeWriter cw, ClassWriter classWriter, CodeFieldStorageMapKind<T> kind) {
        if(written.contains(kind)) return cw;
        written.add(kind);
        for(var dep : kind.getDependencies()) {
            cw = writeFromStorage(written, cw, classWriter, dep);
        }
        for(var value : map.getStorage(kind)) {
            var name = kind.generateName(map, value);
            cost += map.writeCode(kind, this, cw, value) + 3;
            cw.setDataField(name, map.getValueClass(kind));
            cw = getCurrentFunction(cw, classWriter);
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, map.getValueClass(kind).descriptorString(), null, null);
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

        HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
        for(var kind : CodeFieldStorageMapKind.values()) {
            cw = writeFromStorage(written, cw, classWriter, kind);
        }

        cw.methodVisitor().visitInsn(Opcodes.RETURN);
        cw.methodVisitor().visitMaxs(0, 0);
        cw.methodVisitor().visitEnd();

        classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass.descriptorString(), null, null);
    }

}
