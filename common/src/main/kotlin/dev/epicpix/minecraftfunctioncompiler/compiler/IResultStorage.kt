package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.parser.Command

interface IResultStorage {
    fun storesResult(): Boolean
    fun storesSuccess(): Boolean
    fun setResult(context: ICompilationContext, data: LocationData)
    fun addResult(context: ICompilationContext, data: LocationData)
    fun updateSuccess(context: ICompilationContext, failed: Boolean)
    fun checkRequirements(command: Command)
}