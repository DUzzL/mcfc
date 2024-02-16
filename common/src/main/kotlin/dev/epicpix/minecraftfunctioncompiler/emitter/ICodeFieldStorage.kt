package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter
import org.objectweb.asm.ClassWriter
import java.util.*

interface ICodeFieldStorage {
    fun loadUUID(cw: CodeWriter, value: UUID)
    fun loadResourceLocation(cw: CodeWriter, value: DataLocation)
    fun loadFunction(cw: CodeWriter, value: DataLocation)
    fun loadAttribute(cw: CodeWriter, value: DataLocation)
    fun loadServerFunctionManager(cw: CodeWriter)
    fun loadScoreboard(cw: CodeWriter)
    fun generate(classWriter: ClassWriter)
}