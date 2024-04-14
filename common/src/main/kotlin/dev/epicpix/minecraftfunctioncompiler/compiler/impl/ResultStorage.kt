package dev.epicpix.minecraftfunctioncompiler.compiler.impl

import dev.epicpix.minecraftfunctioncompiler.compiler.ICompilationContext
import dev.epicpix.minecraftfunctioncompiler.compiler.IResultStorage
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.parser.Command

class ResultStorage(
    val result: LocationData? = null,
    val success: LocationData? = null
) : IResultStorage {

    private var updatedResult = false
    private var updatedSuccess = false

    override fun storesResult() = result != null
    override fun storesSuccess() = success != null

    override fun setResult(context: ICompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            context.addInstruction(Instructions2.setResultStorage(result!!, data))
        }
    }

    override fun addResult(context: ICompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            context.addInstruction(Instructions2.addResultStorage(result!!, data))
        }
    }

    override fun updateSuccess(context: ICompilationContext, failed: Boolean) {
        if(storesSuccess()) {
            updatedSuccess = true
            context.addInstruction(Instructions2.setResultStorage(success!!, LocationData(integer = if(failed) 0 else 1)))
        }
    }

    override fun checkRequirements(command: Command) {
        if(storesSuccess() && !updatedSuccess) throw UnsupportedOperationException("ResultStorage requirement failed, requires success value but missing data: $command")
        if(storesResult() && !updatedResult) throw UnsupportedOperationException("ResultStorage requirement failed, requires result value but missing data: $command")
    }

}
