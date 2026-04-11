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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.EMIT_SERVER_FUNCTION_MANAGER_NAME;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.RUN_RESULT_FIELD;

public final class CodeFieldStorage {

    public final ClassDesc serverFunctionManagerType;
    public final ClassDesc minecraftServerType;
    private final CodeFieldStorageStorageMap map;

    public CodeFieldStorage(
            ClassDesc serverFunctionManagerType,
            ClassDesc minecraftServerType,
            CodeFieldStorageMap map
    ) {
        this.serverFunctionManagerType = serverFunctionManagerType;
        this.minecraftServerType = minecraftServerType;
        map.checkEntryCount();
        this.map = map.toStorageMap();
    }

    public CodeFieldStorageStorageMap getMap() {
        return map;
    }

    public BytecodeRegister loadAdvancement(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ADVANCEMENT, value);
        return cw.getDataField(CodeFieldStorageMapKind.ADVANCEMENT.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.ADVANCEMENT));
    }

    public BytecodeRegister loadAttribute(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ATTRIBUTE, value);
        return cw.getDataField(CodeFieldStorageMapKind.ATTRIBUTE.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.ATTRIBUTE));
    }

    public BytecodeRegister loadEffect(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.EFFECT, value);
        return cw.getDataField(CodeFieldStorageMapKind.EFFECT.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.EFFECT));
    }

    public BytecodeRegister loadEntityType(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ENTITY_TYPE, value);
        return cw.getDataField(CodeFieldStorageMapKind.ENTITY_TYPE.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.ENTITY_TYPE));
    }

    public BytecodeRegister loadFunction(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.FUNCTION, value);
        return cw.getDataField(CodeFieldStorageMapKind.FUNCTION.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.FUNCTION));
    }

    public BytecodeRegister loadNbtValue(CodeWriter cw, NbtValue value) {
        if(value instanceof NbtValue.Compound compound) {
            map.writeValue(CodeFieldStorageMapKind.NBT_COMPOUND, compound);
            return cw.getDataField(CodeFieldStorageMapKind.NBT_COMPOUND.generateName(map, compound), map.getValueType(CodeFieldStorageMapKind.NBT_COMPOUND));
        }
        map.writeValue(CodeFieldStorageMapKind.NBT_VALUE, value);
        return cw.getDataField(CodeFieldStorageMapKind.NBT_VALUE.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.NBT_VALUE));
    }

    public BytecodeRegister loadPredicate(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.PREDICATE, value);
        return cw.getDataField(CodeFieldStorageMapKind.PREDICATE.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.PREDICATE));
    }

    public BytecodeRegister loadRegexPattern(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.REGEX_PATTERN, value);
        return cw.getDataField(CodeFieldStorageMapKind.REGEX_PATTERN.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.REGEX_PATTERN));
    }

    public BytecodeRegister loadIdentifier(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        return cw.getDataField(CodeFieldStorageMapKind.RESOURCE_LOCATION.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.RESOURCE_LOCATION));
    }

    public BytecodeRegister loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        return cw.getDataField(CodeFieldStorageMapKind.SCOREBOARD_CRITERION.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.SCOREBOARD_CRITERION));
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
        return cw.getDataField(CodeFieldStorageMapKind.TEXT.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.TEXT));
    }

    public BytecodeRegister loadTextStyle(CodeWriter cw, TextStyle value) {
        map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, value);
        if(value.font() != null) {
            map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value.font());
        }
        return cw.getDataField(CodeFieldStorageMapKind.TEXT_STYLE.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.TEXT_STYLE));
    }

    public BytecodeRegister loadUUID(CodeWriter cw, UUID value) {
        map.writeValue(CodeFieldStorageMapKind.UUID, value);
        return cw.getDataField(CodeFieldStorageMapKind.UUID.generateName(map, value), map.getValueType(CodeFieldStorageMapKind.UUID));
    }

    public BytecodeRegister loadServerFunctionManager(CodeWriter cw) {
        return cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType);
    }

    private <T> void writeFromStorage(HashSet<CodeFieldStorageMapKind<?>> written, CodeWriter cw, ClassGenerator classGenerator, CodeFieldStorageMapKind<T> kind) {
        if(written.contains(kind)) return;
        written.add(kind);
        for(var dep : kind.getDependencies()) {
            writeFromStorage(written, cw, classGenerator, dep);
        }
        for(var value : map.getStorage(kind)) {
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, this, cw, value);
            cw.setDataField(name, map.getValueType(kind), writtenValue);
            classGenerator.addFinalField(name, map.getValueType(kind));
        }
    }

    public BytecodeValue getGeneratorMinecraftServer() {
        return new BytecodeValue.Parameter(0, minecraftServerType);
    }

    public byte[] generate(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        return ClassGenerator.createClassGenerator(DATA_CLASS, true, classGenerator -> {
            // Generate per-kind sub-methods to avoid JVM 65535 bytecode limit.
            // Each CodeFieldStorageMapKind gets its own load$N static method.
            HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
            var loadMethodType = MethodTypeDesc.of(ConstantDescs.CD_void, minecraftServerType, serverFunctionManagerType);
            var subMethodNames = new java.util.ArrayList<String>();

            for(var kind : CodeFieldStorageMapKind.values()) {
                if(written.contains(kind)) continue;
                var subCw = new CodeWriter();
                writeFromStorage(written, subCw, classGenerator, kind);
                var rawInstructions = subCw.getInstructions();
                if(rawInstructions.isEmpty()) continue;

                subCw.returnVoid();
                var subName = "load$" + subMethodNames.size();
                var optimized = BytecodeOptimizer.optimizeInstructions(subCw.getInstructions());
                lowLevelInstructions.put(DATA_CLASS.displayName() + "#" + subName, optimized);
                classGenerator.addFunction(subName, optimized, loadMethodType);
                subMethodNames.add(subName);
            }

            // Main load() method: set ServerFunctionManager field, then call each sub-method
            var mainCw = new CodeWriter();
            mainCw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType, new BytecodeValue.Parameter(1, serverFunctionManagerType));
            for(var subName : subMethodNames) {
                var target = new dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget.FunctionTarget(DATA_CLASS, subName, loadMethodType, true, false, false, true);
                mainCw.writeTarget(target, new BytecodeValue.Parameter(0, minecraftServerType), new BytecodeValue.Parameter(1, serverFunctionManagerType));
            }
            mainCw.returnVoid();

            var mainInstructions = BytecodeOptimizer.optimizeInstructions(mainCw.getInstructions());
            lowLevelInstructions.put(DATA_CLASS.displayName() + "#load", mainInstructions);
            classGenerator.addFunction("load", mainInstructions, loadMethodType);

            classGenerator.addFinalField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType);
            classGenerator.addField(RUN_RESULT_FIELD, ConstantDescs.CD_long, 0L);
        });
    }

}
