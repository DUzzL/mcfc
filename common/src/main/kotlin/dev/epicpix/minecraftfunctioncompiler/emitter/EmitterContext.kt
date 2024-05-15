package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData
import org.objectweb.asm.Label

data class EmitterContext(
    val fieldStorage: CodeFieldStorage,
    val codeWriter: CodeWriter,
    val compiledFunctions: Set<DataLocation>,
    val transforms: Map<Int, String> = mapOf(),
    val scopes: Map<Int, Label> = mapOf(),

    val instruction: Instruction,
    val newTransforms: MutableMap<Int, String>,
    val newScopes: MutableMap<Int, Label>,
) {
    fun cloneWith(instruction: Instruction): EmitterContext {
        return copy(transforms = newTransforms, scopes = newScopes, instruction = instruction, newTransforms = newTransforms.toMutableMap(), newScopes = newScopes.toMutableMap())
    }

    fun run() {
        instruction.children.forEach {
            emitInstruction(cloneWith(it))
        }
    }

    fun save(location: String) {
        val loc = instruction.get<LocationData?>(location)
        if(loc?.local == null) throw UnsupportedOperationException("Tried to store variable in a non-defined local location: $loc, $instruction")
        codeWriter.storeLocal(newTransforms[loc.local]!!)
    }

    fun store(location: String, uid: String) {
        val loc = instruction.get<LocationData?>(location)
        if(loc?.local == null) throw UnsupportedOperationException("Tried to store variable in a non-defined local location: $loc, $instruction")
        newTransforms[loc.local] = uid
    }

    private fun storeCreate(location: String, uid: String, type: Class<*>, exec: () -> Unit) {
        store(location, uid)
        codeWriter.storeLocal(uid, type, exec)
    }

    fun runStored(location: String, uidName: String, type: Class<*>) {
        storeCreate(location, codeWriter.generateUniqueLocal(uidName), type) { run() }
    }

    fun load(location: String) {
        val loc = instruction.get<LocationData>(location)
        if(loc.string != null) {
            codeWriter.writeString(loc.string)
        }else if(loc.local != null) {
            codeWriter.loadLocal(newTransforms[loc.local]!!)
        }else if(loc.integer != null) {
            codeWriter.writeInt(loc.integer)
        }
    }

    fun scopeId(location: String) = instruction.get<ScopeData>(location).id
}
