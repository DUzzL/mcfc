package dev.epicpix.minecraftfunctioncompiler

data class GeneratorCommandContext(var entityLocal: String? = null) {
    fun getEntity(cw: CodeWriter) {
        cw.loadLocal(entityLocal!!)
    }

}