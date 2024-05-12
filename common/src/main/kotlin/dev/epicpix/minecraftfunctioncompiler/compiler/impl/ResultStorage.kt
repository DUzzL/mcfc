package dev.epicpix.minecraftfunctioncompiler.compiler.impl

import dev.epicpix.minecraftfunctioncompiler.il.Instructions2
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.parser.Command

class ResultStorage(
    val result: LocationData? = null,
    val success: LocationData? = null
) {

    private var updatedResult = false
    private var updatedSuccess = false

    fun storesResult() = result != null
    fun storesSuccess() = success != null

    fun setResult(context: CompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            context.addInstruction(Instructions2.setResultStorage(result!!, data))
        }
    }

    fun addResult(context: CompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            context.addInstruction(Instructions2.addResultStorage(result!!, data))
        }
    }

    fun updateSuccess(context: CompilationContext, failed: Boolean) {
        if(storesSuccess()) {
            updatedSuccess = true
            context.addInstruction(Instructions2.setResultStorage(success!!, LocationData(integer = if(failed) 0 else 1)))
        }
    }

    fun checkRequirements(command: Command) {
        if(storesSuccess() && !updatedSuccess) throw UnsupportedOperationException("ResultStorage requirement failed, requires success value but missing data: $command")
        if(storesResult() && !updatedResult) throw UnsupportedOperationException("ResultStorage requirement failed, requires result value but missing data: $command")
    }

}
