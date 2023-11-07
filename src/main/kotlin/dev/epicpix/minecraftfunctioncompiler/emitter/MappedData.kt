package dev.epicpix.minecraftfunctioncompiler.emitter

class MappedData {

    companion object {
        val MinecraftServer_getScoreboard = MappedData()
        val MinecraftServer_getPlayerList = MappedData()
        val MinecraftServer_getAllLevels = MappedData()
        val ServerLevel_getAllEntities = MappedData()

        val PlayerList_getPlayers = MappedData()
        val PlayerList_getPlayerByName = MappedData()

        val Entity_getTags = MappedData()
        val Entity_addTag = MappedData()
        val Entity_removeTag = MappedData()
        val Entity_getScoreboardName = MappedData()
        val Entity_kill = MappedData()
        val Entity_level = MappedData()
        val Entity_position = MappedData()
        val Entity_getRotationVector = MappedData()

        val ServerLevel_getEntity = MappedData()
        val ServerFunctionManager_getLibrary = MappedData()
        val ServerFunctionManager_execute = MappedData()
        val ServerFunctionLibrary_getFunctions = MappedData()

        val CommandSourceStack_getEntity = MappedData()
        val CommandSourceStack_getServer = MappedData()
        val CommandSourceStack_withEntity = MappedData()
        val CommandSourceStack_withLevel = MappedData()
        val CommandSourceStack_withPosition = MappedData()
        val CommandSourceStack_withRotation = MappedData()

        val Scoreboard_playerScores = MappedData()
        val Scoreboard_getObjective = MappedData()
        val Scoreboard_getOrCreatePlayerScore = MappedData()
        val Scoreboard_resetPlayerScore = MappedData()
        val Score_setScore = MappedData()
        val Score_getScore = MappedData()

        val ArrayList_size = MappedData()
        val ArrayList_get = MappedData()
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
