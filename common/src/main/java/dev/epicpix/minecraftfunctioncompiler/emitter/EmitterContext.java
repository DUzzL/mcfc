package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.EMITTER_INVALID_LOAD_LOCATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.EMITTER_INVALID_STORE_LOCATION;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.EMITTER_LOAD_LOCATION_NOT_STORED;

public final class EmitterContext {
    private final DataLocation location;
    private final CodeFieldStorage fieldStorage;
    private final CodeWriter codeWriter;
    private final Set<DataLocation> compiledFunctions;
    private final Map<String, BytecodeValue> staticArguments;
    private final @Nullable Map<String, BytecodeRegister> macroLocations;
    private final List<Instruction> instructions;

    private final Int2ObjectMap<BytecodeValue> transforms = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<@Nullable BytecodeLabel> labelMappings = new Int2ObjectOpenHashMap<>();

    private int currentInstructionIndex = 0;

    public EmitterContext(
        DataLocation location,
        CodeFieldStorage fieldStorage,
        CodeWriter codeWriter,
        Set<DataLocation> compiledFunctions,
        Map<String, BytecodeValue> staticArguments,
        @Nullable Map<String, BytecodeRegister> macroLocations,

        List<Instruction> instructions
    ) {
        this.location = location;
        this.fieldStorage = fieldStorage;
        this.codeWriter = codeWriter;
        this.compiledFunctions = compiledFunctions;
        this.staticArguments = staticArguments;
        this.macroLocations = macroLocations;
        this.instructions = instructions;
    }

    public void emitForEach() {
        // Pre-populate transforms for all locals across all instructions.
        // The optimizer can produce IR where a load of local<N> appears before
        // its store in linear instruction order, or in rare edge cases may leave
        // a local reference without a corresponding store instruction.
        // Pre-creating registers here prevents "Cannot load from location" errors.
        for(var instr : instructions) {
            for(var data : instr.data()) {
                if(data instanceof LocationData<?> loc && loc.local() != null) {
                    int local = loc.local();
                    if(!transforms.containsKey(local)) {
                        transforms.put(local, codeWriter.createRegister());
                    }
                }
            }
        }
        for(currentInstructionIndex = 0; currentInstructionIndex < instructions.size(); currentInstructionIndex++) {
            EmitterInstructions.emitInstruction(this);
        }
    }

    public DataLocation location() {
        return location;
    }

    public Instruction instruction() {
        return instructions.get(currentInstructionIndex);
    }

    public CodeFieldStorage fieldStorage() {
        return fieldStorage;
    }

    public CodeWriter codeWriter() {
        return codeWriter;
    }

    public Set<DataLocation> compiledFunctions() {
        return compiledFunctions;
    }

    public @Nullable BytecodeRegister getMacro(String macro) {
        if(macroLocations == null) return null;
        return macroLocations.get(macro);
    }

    public BytecodeLabel mapLabel(String instructionField) {
        return mapLabel(instruction().type().getArgumentIndex(instructionField));
    }

    public BytecodeLabel mapLabel(int instructionIndexField) {
        return mapLabel(Objects.requireNonNull(instruction().<LabelData>get(instructionIndexField)));
    }

    public BytecodeLabel mapLabel(LabelData label) {
        var mapped = labelMappings.get(label.id());
        if(mapped != null) {
            return mapped;
        }
        var bytecodeLabel = codeWriter.createLabel();
        labelMappings.put(label.id(), bytecodeLabel);
        return bytecodeLabel;
    }

    public BytecodeValue load(String instructionField) {
        return load(instruction().type().getArgumentIndex(instructionField));
    }

    public BytecodeValue load(int instructionIndexField) {
        var location = Objects.requireNonNull(instruction().<LocationData<?>>get(instructionIndexField));
        if(location.local() != null) {
            if(!transforms.containsKey((int)location.local())) {
                throw new DiagnosticException(Diagnostic.emitterError(EMITTER_LOAD_LOCATION_NOT_STORED, this.location, "Cannot load from location: " + location + ", nothing is stored in that location"));
            }
            return transforms.get((int)location.local());
        }else if(location.object() instanceof String str) {
            return new BytecodeValue.StringValue(str);
        }else if(location.object() instanceof Integer intVal) {
            return new BytecodeValue.IntegerValue(intVal);
        }else if(location.object() instanceof Float floatVal) {
            return new BytecodeValue.FloatValue(floatVal);
        }else if(location.object() instanceof Double doubleVal) {
            return new BytecodeValue.DoubleValue(doubleVal);
        }else {
            throw new DiagnosticException(Diagnostic.emitterError(EMITTER_INVALID_LOAD_LOCATION, this.location, "Invalid field load location: " + location));
        }
    }

    public void store(int instructionFieldIndex, BytecodeValue value) {
        var location = Objects.requireNonNull(instruction().<LocationData<?>>get(instructionFieldIndex));
        if(location.local() != null) {
            var transform = transforms.get((int)location.local());
            if(transform instanceof BytecodeRegister reg) {
                codeWriter.setValue(reg, value);
            }else {
                var store = codeWriter.createRegister();
                codeWriter.setValue(store, value);
                transforms.put((int)location.local(), store);
            }
        }else {
            throw new DiagnosticException(Diagnostic.emitterError(EMITTER_INVALID_STORE_LOCATION, this.location, "Invalid field store location: " + location));
        }
    }

    public void store(String instructionField, BytecodeValue value) {
        store(instruction().type().getArgumentIndex(instructionField), value);
    }

    public BytecodeValue getStatic(String name) {
        return staticArguments.get(name);
    }

    public void putStatic(String name, BytecodeValue value) {
        staticArguments.put(name, value);
    }

    public void setTransform(int index, BytecodeValue value) {
        transforms.put(index, value);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EmitterContext that = (EmitterContext) o;
        return currentInstructionIndex == that.currentInstructionIndex && Objects.equals(fieldStorage, that.fieldStorage) && Objects.equals(codeWriter, that.codeWriter) && Objects.equals(compiledFunctions, that.compiledFunctions) && Objects.equals(transforms, that.transforms) && Objects.equals(macroLocations, that.macroLocations) && Objects.equals(instructions, that.instructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldStorage, codeWriter, compiledFunctions, transforms, macroLocations, instructions, currentInstructionIndex);
    }

    @Override
    public String toString() {
        return "EmitterContext{" +
            "fieldStorage=" + fieldStorage +
            ", codeWriter=" + codeWriter +
            ", compiledFunctions=" + compiledFunctions +
            ", transforms=" + transforms +
            ", macroLocations=" + macroLocations +
            ", currentInstructionIndex=" + currentInstructionIndex +
            '}';
    }
}
