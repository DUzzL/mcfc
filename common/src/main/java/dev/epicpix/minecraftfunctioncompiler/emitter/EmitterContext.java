package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import org.objectweb.asm.Label;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions.emitInstruction;

public record EmitterContext(
        CodeFieldStorage fieldStorage,
        CodeWriter codeWriter,
        Set<DataLocation> compiledFunctions,
        Map<Integer, String> transforms,
        Map<Integer, Label> scopes,

        Instruction instruction,
        HashMap<Integer, String> newTransforms,
        HashMap<Integer, Label> newScopes
) {
    public EmitterContext cloneWith(Instruction instruction) {
        return new EmitterContext(fieldStorage, codeWriter, compiledFunctions, newTransforms, newScopes, instruction, new HashMap<>(newTransforms), new HashMap<>(newScopes));
    }

    public void run() {
        for(var it : instruction.children()) {
            emitInstruction(cloneWith(it));
        }
    }

    public void save(String location) {
        var loc = instruction.<LocationData<?>>get(location);
        if(loc == null || loc.local() == null) throw new UnsupportedOperationException("Tried to store variable in a non-defined local location: " + loc + ", " + instruction);
        codeWriter.storeLocal(newTransforms.get(loc.local()));
    }

    public void store(String location, String uid) {
        var loc = instruction.<LocationData<?>>get(location);
        if(loc == null || loc.local() == null) throw new UnsupportedOperationException("Tried to store variable in a non-defined local location: " + loc + ", " + instruction);
        newTransforms.put(loc.local(), uid);
    }

    public void runStored(String location, Class<?> type) {
        var uid = codeWriter.generateUniqueLocal();
        store(location, uid);
        var localValue = codeWriter.createLocal(uid, type.descriptorString());
        codeWriter.storeLocal(uid);
        run();
        codeWriter.destroyLocal(localValue);
    }

    public void load(String location) {
        var loc = instruction.<LocationData<?>>get(location);
        if(loc.string() != null) {
            codeWriter.writeString(loc.string());
        }else if(loc.local() != null) {
            codeWriter.loadLocal(newTransforms.get(loc.local()));
        }else if(loc.integer() != null) {
            codeWriter.writeInt(loc.integer());
        }else if(loc.doubleValue() != null) {
            codeWriter.writeDouble(loc.doubleValue());
        }else {
            throw new IllegalArgumentException("Loading imaginary data from " + location);
        }
    }
}
