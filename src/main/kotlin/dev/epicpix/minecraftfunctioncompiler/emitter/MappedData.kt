package dev.epicpix.minecraftfunctioncompiler.emitter

class MappedData {

    companion object {
        val ServerLevel_GetEntity = MappedData()
    }

    internal var generator: ((CodeWriter) -> Unit)? = null

    fun generate(cw: CodeWriter) = generator!!(cw)

}
