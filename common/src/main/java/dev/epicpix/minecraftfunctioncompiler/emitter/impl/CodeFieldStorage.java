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

    // Flushes current CodeWriter as a sub-method if it exceeds estimated size.
    // Returns a fresh CodeWriter if flushed, otherwise the same one.
    private CodeWriter maybeFlushSubMethod(CodeWriter cw, ClassGenerator classGenerator, MethodTypeDesc loadMethodType, java.util.List<String> subMethodNames, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, int threshold) {
        if(cw.getInstructions().size() < threshold) return cw;
        cw.returnVoid();
        var subName = "load$" + subMethodNames.size();
        var optimized = BytecodeOptimizer.optimizeInstructions(cw.getInstructions());
        lowLevelInstructions.put(DATA_CLASS.displayName() + "#" + subName, optimized);
        classGenerator.addFunction(subName, optimized, loadMethodType);
        subMethodNames.add(subName);
        return new CodeWriter();
    }

    private <T> CodeWriter writeFromStorageChunked(HashSet<CodeFieldStorageMapKind<?>> written, CodeWriter cw, ClassGenerator classGenerator, CodeFieldStorageMapKind<T> kind, MethodTypeDesc loadMethodType, java.util.List<String> subMethodNames, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, int threshold, HashMap<CodeFieldStorageMapKind<?>, java.util.HashSet<Object>> processed) {
        if(written.contains(kind)) return cw;
        written.add(kind);
        for(var dep : kind.getDependencies()) {
            cw = writeFromStorageChunked(written, cw, classGenerator, dep, loadMethodType, subMethodNames, lowLevelInstructions, threshold, processed);
        }
        // Process values; writeCode may register new values in storage (e.g. nested TextStyle for hover events).
        // Loop until storage stabilizes.
        var kindProcessed = processed.computeIfAbsent(kind, k -> new java.util.HashSet<>());
        while(true) {
            var storage = map.getStorage(kind);
            boolean addedAny = false;
            for(var value : storage) {
                if(!kindProcessed.add(value)) continue;
                addedAny = true;
                // Process dependencies that might have been registered newly
                for(var dep : kind.getDependencies()) {
                    cw = processNewValues(written, cw, classGenerator, dep, loadMethodType, subMethodNames, lowLevelInstructions, threshold, processed);
                }
                var name = kind.generateName(map, value);
                var writtenValue = map.writeCode(kind, this, cw, value);
                cw.setDataField(name, map.getValueType(kind), writtenValue);
                classGenerator.addFinalField(name, map.getValueType(kind));
                cw = maybeFlushSubMethod(cw, classGenerator, loadMethodType, subMethodNames, lowLevelInstructions, threshold);
            }
            if(!addedAny) break;
        }
        return cw;
    }

    private <T> CodeWriter processNewValues(HashSet<CodeFieldStorageMapKind<?>> written, CodeWriter cw, ClassGenerator classGenerator, CodeFieldStorageMapKind<T> kind, MethodTypeDesc loadMethodType, java.util.List<String> subMethodNames, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, int threshold, HashMap<CodeFieldStorageMapKind<?>, java.util.HashSet<Object>> processed) {
        if(!written.contains(kind)) return cw; // not yet processed at all — will be handled later
        var kindProcessed = processed.computeIfAbsent(kind, k -> new java.util.HashSet<>());
        for(var dep : kind.getDependencies()) {
            cw = processNewValues(written, cw, classGenerator, dep, loadMethodType, subMethodNames, lowLevelInstructions, threshold, processed);
        }
        var storage = map.getStorage(kind);
        for(var value : storage) {
            if(!kindProcessed.add(value)) continue;
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, this, cw, value);
            cw.setDataField(name, map.getValueType(kind), writtenValue);
            classGenerator.addFinalField(name, map.getValueType(kind));
            cw = maybeFlushSubMethod(cw, classGenerator, loadMethodType, subMethodNames, lowLevelInstructions, threshold);
        }
        return cw;
    }

    public BytecodeValue getGeneratorMinecraftServer() {
        return new BytecodeValue.Parameter(0, minecraftServerType);
    }

    // Approx instruction count threshold — keeps generated bytecode under JVM's 65535 byte method limit.
    // Actual bytecode size varies per instruction (~5-20 bytes avg), so 2000 instructions ≈ 20-40KB.
    private static final int INSTRUCTION_CHUNK_THRESHOLD = 2000;

    private <T> CodeWriter processRemainingValues(CodeFieldStorageMapKind<T> kind, CodeWriter cw, ClassGenerator classGenerator, MethodTypeDesc loadMethodType, java.util.List<String> subMethodNames, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions, HashMap<CodeFieldStorageMapKind<?>, java.util.HashSet<Object>> processed, boolean[] anyNewBox) {
        var kindProcessed = processed.computeIfAbsent(kind, k -> new java.util.HashSet<>());
        for(var value : map.getStorage(kind)) {
            if(!kindProcessed.add(value)) continue;
            anyNewBox[0] = true;
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, this, cw, value);
            cw.setDataField(name, map.getValueType(kind), writtenValue);
            classGenerator.addFinalField(name, map.getValueType(kind));
            cw = maybeFlushSubMethod(cw, classGenerator, loadMethodType, subMethodNames, lowLevelInstructions, INSTRUCTION_CHUNK_THRESHOLD);
        }
        return cw;
    }

    public byte[] generate(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        return ClassGenerator.createClassGenerator(DATA_CLASS, true, classGenerator -> {
            // Split initialization across multiple load$N methods to stay under JVM 65535 byte/method limit.
            HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
            var processed = new HashMap<CodeFieldStorageMapKind<?>, java.util.HashSet<Object>>();
            var loadMethodType = MethodTypeDesc.of(ConstantDescs.CD_void, minecraftServerType, serverFunctionManagerType);
            var subMethodNames = new java.util.ArrayList<String>();

            var currentCw = new CodeWriter();
            for(var kind : CodeFieldStorageMapKind.values()) {
                currentCw = writeFromStorageChunked(written, currentCw, classGenerator, kind, loadMethodType, subMethodNames, lowLevelInstructions, INSTRUCTION_CHUNK_THRESHOLD, processed);
            }
            // Final pass to catch any values added during last iteration
            boolean anyNew = true;
            while(anyNew) {
                boolean[] anyNewBox = {false};
                for(var kind : CodeFieldStorageMapKind.values()) {
                    currentCw = processRemainingValues(kind, currentCw, classGenerator, loadMethodType, subMethodNames, lowLevelInstructions, processed, anyNewBox);
                }
                anyNew = anyNewBox[0];
            }
            // Flush final chunk
            if(!currentCw.getInstructions().isEmpty()) {
                currentCw.returnVoid();
                var subName = "load$" + subMethodNames.size();
                var optimized = BytecodeOptimizer.optimizeInstructions(currentCw.getInstructions());
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
