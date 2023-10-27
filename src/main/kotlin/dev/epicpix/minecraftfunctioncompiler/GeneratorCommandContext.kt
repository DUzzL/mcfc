package dev.epicpix.minecraftfunctioncompiler

class GeneratorCommandContext(var entityLocal: String? = null) {
    fun getEntity(cw: CodeWriter) {
        cw.loadLocal(entityLocal!!)
    }

}