package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import org.objectweb.asm.ClassWriter
import java.util.*

interface ICodeFieldStorage {
    fun loadUUID(cw: ICodeWriter, value: UUID)
    fun loadResourceLocation(cw: ICodeWriter, value: DataLocation)
    fun loadFunction(cw: ICodeWriter, value: DataLocation)
    fun loadAttribute(cw: ICodeWriter, value: DataLocation)
    fun loadServerFunctionManager(cw: ICodeWriter)
    fun loadScoreboard(cw: ICodeWriter)
    fun generate(classWriter: ClassWriter)
}