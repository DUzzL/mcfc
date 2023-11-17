package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.il.LocationData

class ResultStorage(
    private val result: LocationData? = null,
    private val success: LocationData? = null
) {

    private var updatedResult = false
    private var updatedSuccess = false

    fun storesResult() = result != null
    fun storesSuccess() = success != null

    fun setResult(context: CompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            TODO("setResult($data)")
        }
    }

    fun addResult(context: CompilationContext, data: LocationData) {
        if(storesResult()) {
            updatedResult = true
            TODO("addResult($data)")
        }
    }

    fun updateSuccess(context: CompilationContext, failed: Boolean) {
        if(storesSuccess()) {
            updatedSuccess = true
            TODO("updateSuccess($failed)")
        }
    }

}