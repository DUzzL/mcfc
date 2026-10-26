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
import java.util.LinkedHashMap;
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
        return loadField(cw, CodeFieldStorageMapKind.ADVANCEMENT, value);
    }

    public BytecodeRegister loadAttribute(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ATTRIBUTE, value);
        return loadField(cw, CodeFieldStorageMapKind.ATTRIBUTE, value);
    }

    public BytecodeRegister loadEffect(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.EFFECT, value);
        return loadField(cw, CodeFieldStorageMapKind.EFFECT, value);
    }

    public BytecodeRegister loadEntityType(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.ENTITY_TYPE, value);
        return loadField(cw, CodeFieldStorageMapKind.ENTITY_TYPE, value);
    }

    public BytecodeRegister loadFunction(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.FUNCTION, value);
        return loadField(cw, CodeFieldStorageMapKind.FUNCTION, value);
    }

    public BytecodeRegister loadNbtValue(CodeWriter cw, NbtValue value) {
        if(value instanceof NbtValue.Compound compound) {
            map.writeValue(CodeFieldStorageMapKind.NBT_COMPOUND, compound);
            return loadField(cw, CodeFieldStorageMapKind.NBT_COMPOUND, compound);
        }
        map.writeValue(CodeFieldStorageMapKind.NBT_VALUE, value);
        return loadField(cw, CodeFieldStorageMapKind.NBT_VALUE, value);
    }

    public BytecodeRegister loadPredicate(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        map.writeValue(CodeFieldStorageMapKind.PREDICATE, value);
        return loadField(cw, CodeFieldStorageMapKind.PREDICATE, value);
    }

    public BytecodeRegister loadRegexPattern(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.REGEX_PATTERN, value);
        return loadField(cw, CodeFieldStorageMapKind.REGEX_PATTERN, value);
    }

    public BytecodeRegister loadIdentifier(CodeWriter cw, DataLocation value) {
        map.writeValue(CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
        return loadField(cw, CodeFieldStorageMapKind.RESOURCE_LOCATION, value);
    }

    public BytecodeRegister loadScoreboardCriterion(CodeWriter cw, String value) {
        map.writeValue(CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
        return loadField(cw, CodeFieldStorageMapKind.SCOREBOARD_CRITERION, value);
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

    /** Loads the field of an already registered value of a field-per-value kind. */
    private <T> BytecodeRegister loadField(CodeWriter cw, CodeFieldStorageMapKind<T> kind, T value) {
        var owner = dataFieldOwner(kind, map.checkIndex(kind, value));
        return cw.getDataField(owner, kind.generateName(map, value), map.getValueType(kind));
    }

    private <T> BytecodeRegister loadArrayElement(CodeWriter cw, CodeFieldStorageMapKind<T> kind, T value) {
        map.writeValue(kind, value);
        var type = map.getValueType(kind);
        var array = cw.getDataField(dataFieldOwner(kind, 0), kind.generateName(map, value), type.arrayType());
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
        return loadField(cw, CodeFieldStorageMapKind.TEXT, value);
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
        return loadField(cw, CodeFieldStorageMapKind.TEXT_STYLE, value);
    }

    public BytecodeRegister loadUUID(CodeWriter cw, UUID value) {
        map.writeValue(CodeFieldStorageMapKind.UUID, value);
        return loadField(cw, CodeFieldStorageMapKind.UUID, value);
    }

    public BytecodeRegister loadServerFunctionManager(CodeWriter cw) {
        return cw.getDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType);
    }


    private static final String DATA_CLASS_NAME = DATA_CLASS.descriptorString().substring(1, DATA_CLASS.descriptorString().length() - 1);

    /**
     * Values per data shard class. A value's field lives in shard {@code index / FIELDS_PER_SHARD} of its kind,
     * so its owner is known while compiled functions reference it, before the data classes are generated.
     * Shard classes only declare fields (one constant pool entry per field); the code that fills them lives in
     * loader classes. One class for everything overflowed its 65535-entry constant pool with ~20k values.
     */
    static final int FIELDS_PER_SHARD = 4096;

    /** Methods per loader class; a loader whose constant pool still overflows is split in halves. */
    private static final int CHUNKS_PER_LOADER = 8;

    /** The class declaring the field of {@code kind}'s value at {@code index} (array-backed kinds: index 0). */
    public static ClassDesc dataFieldOwner(CodeFieldStorageMapKind<?> kind, int index) {
        if(index < 0) throw new IllegalStateException(kind + " value is not registered");
        int shard = kind.isArrayBacked() ? 0 : index / FIELDS_PER_SHARD;
        return ClassDesc.ofInternalName(DATA_CLASS_NAME + "$f" + kind.id() + "_" + shard);
    }

    /** True for the main data class and every generated data shard or loader class. */
    public static boolean isDataClass(ClassDesc type) {
        var descriptor = type.descriptorString();
        int length = DATA_CLASS_NAME.length() + 1;
        return descriptor.startsWith(DATA_CLASS_NAME, 1)
            && (descriptor.length() == length + 1 || descriptor.charAt(length) == '$');
    }

    /** One generated data class: field shards and loaders first, the main {@code DATA_CLASS} last. */
    public record DataClass(ClassDesc type, byte[] bytes) {
        public String binaryName() {
            var descriptor = type.descriptorString();
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
    }

    private record Chunk(String name, List<BytecodeInstruction> instructions) {}

    /** Per-kind generation state: values with insertion index below {@code processed.get(kind)} are written. */
    private final class Generation {
        final HashSet<CodeFieldStorageMapKind<?>> written = new HashSet<>();
        final HashMap<CodeFieldStorageMapKind<?>, Integer> processed = new HashMap<>();
        final HashMap<CodeFieldStorageMapKind<?>, Integer> arrayLengths = new HashMap<>();
        final LinkedHashMap<ClassDesc, LinkedHashMap<String, ClassDesc>> fields = new LinkedHashMap<>();
        final List<Chunk> chunks = new ArrayList<>();
        final HashMap<String, List<BytecodeInstruction>> lowLevelInstructions;
        CodeWriter cw = new CodeWriter();

        Generation(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
            this.lowLevelInstructions = lowLevelInstructions;
        }

        boolean hasPending(CodeFieldStorageMapKind<?> kind) {
            return processed.getOrDefault(kind, 0) < map.storageSize(kind);
        }

        void addField(ClassDesc owner, String name, ClassDesc type) {
            fields.computeIfAbsent(owner, _ -> new LinkedHashMap<>()).put(name, type);
        }

        /** Writes the next unwritten value of {@code kind}. */
        <T> void writeNext(CodeFieldStorageMapKind<T> kind) {
            int index = processed.getOrDefault(kind, 0);
            processed.put(kind, index + 1);
            var value = map.valueAt(kind, index);
            var name = kind.generateName(map, value);
            var writtenValue = map.writeCode(kind, CodeFieldStorage.this, cw, value);
            var type = map.getValueType(kind);
            var owner = dataFieldOwner(kind, index);
            if(kind.isArrayBacked()) {
                if(index == 0) {
                    int length = map.storageSize(kind);
                    arrayLengths.put(kind, length);
                    cw.setDataField(owner, name, type.arrayType(), cw.newArray(new BytecodeValue.IntegerValue(length), type));
                    addField(owner, name, type.arrayType());
                }else if(index >= arrayLengths.get(kind)) {
                    throw new IllegalStateException(kind + " value registered after its array was sized: " + value);
                }
                cw.setArrayValue(cw.getDataField(owner, name, type.arrayType()), new BytecodeValue.IntegerValue(index), writtenValue);
            }else {
                cw.setDataField(owner, name, type, writtenValue);
                addField(owner, name, type);
            }
            if(cw.size() >= INSTRUCTION_CHUNK_THRESHOLD) flushChunk();
        }

        /** Ends the current load$N method (keeps generated bytecode under the JVM's 65535 byte method limit). */
        void flushChunk() {
            if(cw.size() == 0) return;
            cw.returnVoid();
            var subName = "load$" + chunks.size();
            var optimized = BytecodeOptimizer.optimizeInstructions(cw.getInstructions());
            lowLevelInstructions.put(DATA_CLASS.displayName() + "#" + subName, optimized);
            chunks.add(new Chunk(subName, optimized));
            cw = new CodeWriter();
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

    public List<DataClass> generate(HashMap<String, List<BytecodeInstruction>> lowLevelInstructions) {
        // Split initialization across multiple load$N methods to stay under JVM 65535 byte/method limit.
        var loadMethodType = MethodTypeDesc.of(ConstantDescs.CD_void, minecraftServerType, serverFunctionManagerType);
        var gen = new Generation(lowLevelInstructions);

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
        gen.flushChunk();

        var classes = new ArrayList<DataClass>();
        // Fields are not final: they are assigned from the loader classes, not from their declaring class.
        for(var shard : gen.fields.entrySet()) {
            classes.add(new DataClass(shard.getKey(), ClassGenerator.createClassGenerator(shard.getKey(), true, classGenerator -> {
                for(var field : shard.getValue().entrySet()) classGenerator.addField(field.getKey(), field.getValue(), null);
            })));
        }
        var calls = new ArrayList<InstructionTarget>(gen.chunks.size());
        for(int start = 0; start < gen.chunks.size(); start += CHUNKS_PER_LOADER) {
            buildLoader(gen.chunks.subList(start, Math.min(start + CHUNKS_PER_LOADER, gen.chunks.size())), loadMethodType, classes, calls);
        }

        // Main load() method: set ServerFunctionManager field, then call each sub-method in order
        var mainCw = new CodeWriter();
        mainCw.setDataField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType, new BytecodeValue.Parameter(1, serverFunctionManagerType));
        for(var target : calls) {
            mainCw.writeTarget(target, new BytecodeValue.Parameter(0, minecraftServerType), new BytecodeValue.Parameter(1, serverFunctionManagerType));
        }
        mainCw.returnVoid();

        var mainInstructions = BytecodeOptimizer.optimizeInstructions(mainCw.getInstructions());
        lowLevelInstructions.put(DATA_CLASS.displayName() + "#load", mainInstructions);
        classes.add(new DataClass(DATA_CLASS, ClassGenerator.createClassGenerator(DATA_CLASS, true, classGenerator -> {
            classGenerator.addFunction("load", mainInstructions, loadMethodType);
            classGenerator.addFinalField(EMIT_SERVER_FUNCTION_MANAGER_NAME, serverFunctionManagerType);
        })));
        return classes;
    }

    private static void buildLoader(List<Chunk> chunks, MethodTypeDesc loadMethodType, List<DataClass> classes, List<InstructionTarget> calls) {
        var type = ClassDesc.ofInternalName(DATA_CLASS_NAME + "$l" + classes.size());
        byte[] bytes;
        try {
            bytes = ClassGenerator.createClassGenerator(type, true, classGenerator -> {
                for(var chunk : chunks) classGenerator.addFunction(chunk.name(), chunk.instructions(), loadMethodType);
            });
        } catch(IllegalArgumentException e) {
            // Constant pool overflow ("... is not a valid index"): spread the chunks over two classes.
            if(chunks.size() == 1) throw e;
            int middle = chunks.size() / 2;
            buildLoader(chunks.subList(0, middle), loadMethodType, classes, calls);
            buildLoader(chunks.subList(middle, chunks.size()), loadMethodType, classes, calls);
            return;
        }
        classes.add(new DataClass(type, bytes));
        for(var chunk : chunks) {
            calls.add(new InstructionTarget.FunctionTarget(type, chunk.name(), loadMethodType, true, false, false, true));
        }
    }

}
