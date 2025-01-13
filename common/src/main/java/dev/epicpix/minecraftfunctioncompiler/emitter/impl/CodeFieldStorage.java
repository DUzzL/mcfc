package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeOptimizer;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.loader.ClassGenerator;
import org.objectweb.asm.Type;

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

    public BytecodeRegister loadAttribute(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ATTRIBUTE, value);
        return cw.getDataField(CodeFieldStorageMapKind.ATTRIBUTE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.ATTRIBUTE));
    }

    public BytecodeRegister loadEffect(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.EFFECT, value);
        return cw.getDataField(CodeFieldStorageMapKind.EFFECT.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.EFFECT));
    }

    public BytecodeRegister loadFunction(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.FUNCTION, value);
        return cw.getDataField(CodeFieldStorageMapKind.FUNCTION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.FUNCTION));
    }

    public BytecodeRegister loadNbtValue(CodeWriter cw, NbtValue value) {
        map.writeValue(CodeFieldStorageMapKind.NBT_VALUE, value);
        return cw.getDataField(CodeFieldStorageMapKind.NBT_VALUE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.NBT_VALUE));
    }

    public BytecodeRegister loadRegexPattern(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.REGEX_PATTERN, value);
        return cw.getDataField(CodeFieldStorageMapKind.REGEX_PATTERN.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.REGEX_PATTERN));
    }

    public BytecodeRegister loadResourceLocation(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        return cw.getDataField(CodeFieldStorageMapKind.RESOURCE_LOCATION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public BytecodeRegister loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        return cw.getDataField(CodeFieldStorageMapKind.SCOREBOARD_CRITERION.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
    }

    public BytecodeRegister loadText(CodeWriter cw, Text value) {
        value.forEachComponent(text -> {
            if(!text.style().isEmpty()) {
                map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, text.style());
                if (text.style().font() != null) {
                    map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, text.style().font());
                }
            }
        });
        map.writeValue(CodeFieldStorageMapKind.TEXT, value);
        return cw.getDataField(CodeFieldStorageMapKind.TEXT.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.TEXT));
    }

    public BytecodeRegister loadTextStyle(CodeWriter cw, TextStyle value) {
        map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, value);
        if(value.font() != null) {
            map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value.font());
        }
        return cw.getDataField(CodeFieldStorageMapKind.TEXT_STYLE.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.TEXT_STYLE));
    }

    public BytecodeRegister loadUUID(CodeWriter cw, UUID value) {
        map.writeValue(CodeFieldStorageMapKind.UUID, value);
        return cw.getDataField(CodeFieldStorageMapKind.UUID.generateName(map, value), map.getValueClass(CodeFieldStorageMapKind.UUID));
    }

    public BytecodeRegister loadServerFunctionManager(CodeWriter cw) {
        return cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass);
    }

    private <T> CodeWriter writeFromStorage(HashSet<CodeFieldStorageMapKind<?>> written, CodeWriter cw, ClassGenerator classGenerator, CodeFieldStorageMapKind<T> kind) {
        if(written.contains(kind)) return cw;
        written.add(kind);
        for(var dep : kind.getDependencies()) {
            cw = writeFromStorage(written, cw, classGenerator, dep);
        }
        for(var value : map.getStorage(kind)) {
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, this, cw, value);
            cw.setDataField(name, map.getValueClass(kind), writtenValue);
            classGenerator.addFinalField(name, Type.getType(map.getValueClass(kind)));
        }

        return cw;
    }

    public byte[] generate(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        var classGenerator = ClassGenerator.createClassGenerator(DATA_CLASS, true);

        var cw = new CodeWriter();

        cw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerClass, new BytecodeValue.Parameter(1, serverFunctionManagerClass));

        HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
        for(var kind : CodeFieldStorageMapKind.values()) {
            cw = writeFromStorage(written, cw, classGenerator, kind);
        }

        cw.returnVoid();

        var instructions = BytecodeOptimizer.optimizeInstructions(cw.getInstructions());
        lowLevelInstructions.put(DATA_CLASS.replace('/', '.') + "#load", instructions);

        classGenerator.addFunction("load", instructions, Type.VOID_TYPE, Type.getType(minecraftServerClass), Type.getType(serverFunctionManagerClass));

        classGenerator.addFinalField(EMIT_SERVER_FUNCTION_MANAGER_NAME, Type.getType(serverFunctionManagerClass));

        return classGenerator.finish();
    }

}
