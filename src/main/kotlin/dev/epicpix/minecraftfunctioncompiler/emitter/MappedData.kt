package dev.epicpix.minecraftfunctioncompiler.emitter

class MappedData {

    companion object {
        val ServerLevel_getEntity = MappedData()
        val ServerFunctionManager_getLibrary = MappedData()
        val ServerFunctionManager_execute = MappedData()
        val ServerFunctionLibrary_getFunctions = MappedData()

        val Scoreboard_playerScores = MappedData()

        val ArrayList_size = MappedData()
        val List_size = MappedData()
        val List_get = MappedData()
        val Set_size = MappedData()
        val Set_contains = MappedData()
        val Map_keys = MappedData()
        val Map_get = MappedData()
        val Iterator_next = MappedData()
        val Iterator_hasNext = MappedData()
        val Iterable_iterator = MappedData()

        val Math_floorDiv = MappedData()
        val Math_floorMod = MappedData()
        val Math_min = MappedData()
        val Math_max = MappedData()
    }

    internal var generator: ((CodeWriter) -> Unit)? = null

    fun generate(cw: CodeWriter) = generator!!(cw)

}
