package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextHoverEvent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
import dev.epicpix.minecraftfunctioncompiler.emitter.ExtensionFunctions;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeOptimizer;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.loader.ClassGenerator;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.EMIT_SERVER_FUNCTION_MANAGER_NAME;

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

    public BytecodeRegister loadObjectiveRef(CodeWriter cw, String objective) {
        return loadArrayElement(cw, CodeFieldStorageMapKind.OBJECTIVE_REF, objective);
    }

    public BytecodeRegister loadScoreHolder(CodeWriter cw, String holder) {
        return loadArrayElement(cw, CodeFieldStorageMapKind.SCORE_HOLDER, holder);
    }

    public BytecodeRegister loadScoreRef(CodeWriter cw, String holder, String objective) {
        return loadArrayElement(cw, CodeFieldStorageMapKind.SCORE_REF, new ScoreRefKey(holder, objective));
    }

    private static final InstructionTarget ARRAY_ELEMENT = InstructionTarget.FunctionTarget.staticFunction(ExtensionFunctions.class, "arrayElement", Object.class, Object[].class, int.class);

    private <T> BytecodeRegister loadArrayElement(CodeWriter cw, CodeFieldStorageMapKind<T> kind, T value) {
        map.writeValue(kind, value);
        var type = map.getValueType(kind);
        var array = cw.getDataField(kind.generateName(map, value), type.arrayType());
        var index = new BytecodeValue.IntegerValue(map.checkIndex(kind, value));
        var accessor = map.getValue(kind).elementAccessor();
        if(accessor != null) return cw.writeTarget(accessor, array, index);
        return cw.castType(cw.writeTarget(ARRAY_ELEMENT, array, index), type);
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
        // Pre-register transitively-referenced sub-styles before this style itself.
        // The data class splits load() into chunked load$N methods that run
        // sequentially, and the per-kind iteration in CodeFieldStorage.generate()
        // emits PUTSTATICs in registration order. This style's writeCode (in
        // CodeFieldStorageMapImpl.TEXT_STYLE) inlines a writeTextValue(hoverText)
        // for SHOW_TEXT hover events, which emits GETSTATIC against each child
        // text's style field. Without this pre-walk those sub-styles would only
        // get registered AT writeCode time — past this style's PUTSTATIC — and
        // the GETSTATICs would fire against still-uninitialised fields,
        // producing a NullPointerException inside MutableComponent.withStyle.
        // forEachComponent walks deepest-first so children land at lower
        // indices than the outer style and are emitted in earlier load$N chunks.
        if(!value.isEmpty()
                && value.hoverEvent() != null
                && value.hoverEvent().action() == TextHoverEvent.Action.SHOW_TEXT
                && value.hoverEvent().value() instanceof Text hoverText) {
            hoverText.forEachComponent(text -> {
                if(!text.style().isEmpty()) {
                    map.writeValue(CodeFieldStorageMapKind.TEXT_STYLE, text.style());
                    if(text.style().font() != null) {
                        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, text.style().font());
                    }
                }
            });
        }
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
    private CodeWriter maybeFlushSubMethod(CodeWriter cw, ClassGenerator classGenerator, MethodTypeDesc loadMethodType, List<String> subMethodNames, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        if(cw.size() < INSTRUCTION_CHUNK_THRESHOLD) return cw;
        cw.returnVoid();
        var subName = "load$" + subMethodNames.size();
        var optimized = BytecodeOptimizer.optimizeInstructions(cw.getInstructions());
        lowLevelInstructions.put(DATA_CLASS.displayName() + "#" + subName, optimized);
        classGenerator.addFunction(subName, optimized, loadMethodType);
        subMethodNames.add(subName);
        return new CodeWriter();
    }

    /** Per-kind generation state: values with insertion index below {@code processed.get(kind)} are written. */
    private final class Generation {
        final HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
        final HashMap<CodeFieldStorageMapKind<?>, Integer> processed = new HashMap<>();
        final ClassGenerator classGenerator;
        final HashMap<CodeFieldStorageMapKind<?>, Integer> arrayLengths = new HashMap<>();
        final MethodTypeDesc loadMethodType;
        final List<String> subMethodNames = new ArrayList<>();
        final HashMap<String, List<BytecodeInstruction>> lowLevelInstructions;
        CodeWriter cw = new CodeWriter();

        Generation(ClassGenerator classGenerator, MethodTypeDesc loadMethodType, HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
            this.classGenerator = classGenerator;
            this.loadMethodType = loadMethodType;
            this.lowLevelInstructions = lowLevelInstructions;
        }

        boolean hasPending(CodeFieldStorageMapKind<?> kind) {
            return processed.getOrDefault(kind, 0) < map.storageSize(kind);
        }

        /** Writes the next unwritten value of {@code kind}. */
        <T> void writeNext(CodeFieldStorageMapKind<T> kind) {
            int index = processed.getOrDefault(kind, 0);
            processed.put(kind, index + 1);
            var value = map.valueAt(kind, index);
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, CodeFieldStorage.this, cw, value);
            var type = map.getValueType(kind);
            if(kind.isArrayBacked()) {
                if(index == 0) {
                    int length = map.storageSize(kind);
                    arrayLengths.put(kind, length);
                    cw.setDataField(name, type.arrayType(), cw.newArray(new BytecodeValue.IntegerValue(length), type));
                    classGenerator.addFinalField(name, type.arrayType());
                }else if(index >= arrayLengths.get(kind)) {
                    throw new IllegalStateException(kind + " value registered after its array was sized: " + value);
                }
                cw.setArrayValue(cw.getDataField(name, type.arrayType()), new BytecodeValue.IntegerValue(index), writtenValue);
            }else {
                cw.setDataField(name, type, writtenValue);
                classGenerator.addFinalField(name, type);
            }
            cw = maybeFlushSubMethod(cw, classGenerator, loadMethodType, subMethodNames, lowLevelInstructions);
        }

        /** Writes all values of {@code kind} after its dependencies; writeCode may register new values (loops until stable). */
        void writeKind(CodeFieldStorageMapKind<?> kind) {
            if(!written.add(kind)) return;
            for(var dep : kind.getDependencies()) writeKind(dep);
            while(hasPending(kind)) {
                // Values of dependencies registered while writing earlier values must be written first.
                for(var dep : kind.getDependencies()) writeNewValues(dep);
                writeNext(kind);
            }
        }

        /** Writes values registered after {@code kind} was written (dependencies first). */
        void writeNewValues(CodeFieldStorageMapKind<?> kind) {
            if(!written.contains(kind)) return; // not yet processed at all — will be handled later
            for(var dep : kind.getDependencies()) writeNewValues(dep);
            while(hasPending(kind)) writeNext(kind);
        }
    }

    public BytecodeValue getGeneratorMinecraftServer() {
        return new BytecodeValue.Parameter(0, minecraftServerType);
    }

    // Approx instruction count threshold — keeps generated bytecode under JVM's 65535 byte method limit.
    // Actual bytecode size varies per instruction (~5-20 bytes avg), so 2000 instructions ≈ 20-40KB.
    private static final int INSTRUCTION_CHUNK_THRESHOLD = 2000;

    public byte[] generate(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        return ClassGenerator.createClassGenerator(DATA_CLASS, true, classGenerator -> {
            // Split initialization across multiple load$N methods to stay under JVM 65535 byte/method limit.
            var loadMethodType = MethodTypeDesc.of(ConstantDescs.CD_void, minecraftServerType, serverFunctionManagerType);
            var gen = new Generation(classGenerator, loadMethodType, lowLevelInstructions);
            var subMethodNames = gen.subMethodNames;

            for(var kind : CodeFieldStorageMapKind.values()) {
                gen.writeKind(kind);
            }
            // Final pass to catch any values added during last iteration
            boolean anyNew = true;
            while(anyNew) {
                anyNew = false;
                for(var kind : CodeFieldStorageMapKind.values()) {
                    while(gen.hasPending(kind)) {
                        anyNew = true;
                        gen.writeNext(kind);
                    }
                }
            }
            var currentCw = gen.cw;
            // Flush final chunk
            if(currentCw.size() != 0) {
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
        });
    }

}
