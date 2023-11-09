package dev.epicpix.minecraftfunctioncompiler.il

import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import net.minecraft.resources.ResourceLocation
import java.util.*

data class LocationData(
    val local: Int? = null,
    val string: String? = null,
)

interface InstructionData

data class InstructionDataLoop(val loopId: Int) : InstructionData
data class InstructionDataLocation(val local: LocationData) : InstructionData
data class InstructionDataLocationBoolean(val local: LocationData, val value: Boolean) : InstructionData
data class InstructionDataLocationString(val local: LocationData, val value: String) : InstructionData
data class InstructionDataLocationUUID(val local: LocationData, val value: UUID) : InstructionData
data class InstructionDataLocationLocation(val local: LocationData, val local2: LocationData) : InstructionData
data class InstructionDataIntLoop(val value: Int, var loopId: Int) : InstructionData
data class InstructionDataLocationInt(val local: LocationData, val value: Int) : InstructionData
data class InstructionDataLocationLocationWorldCoordinates(val local: LocationData, val local2: LocationData, val coords: WorldCoordinates) : InstructionData
data class InstructionDataLocationLocationLocation(val local: LocationData, val local2: LocationData, val local3: LocationData) : InstructionData

data class InstructionDataFunctionCall(val functions: List<ResourceLocation>, val entity: LocationData?, val world: LocationData?, val position: LocationData?, val rotation: LocationData?) : InstructionData
data class InstructionDataRunScoreOperation(val target: LocationData, val source: LocationData, val operation: ScoreboardOperation, val exceptionLoopId: Int) : InstructionData
data class InstructionDataCheckScoreRange(val score: LocationData, val range: IntRangeArgument, val negate: Boolean) : InstructionData
data class InstructionDataCheckScoreCompare(val target: LocationData, val source: LocationData, val compareOperation: ScoreboardCompareOperation) : InstructionData

enum class ScoreboardOperation { SET, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, MIN, MAX, SWAP }
enum class ScoreboardCompareOperation { LT, LE, EQ, NE, GE, GT }

data class Instruction(val type: InstructionType, val data: InstructionData?, val children: MutableList<Instruction> = mutableListOf()) {
    fun with(instruction: Instruction): Instruction {
        children.add(instruction)
        return this
    }
    fun countInstructions(): Int {
        var result = 1
        children.forEach { result += it.countInstructions() }
        return result
    }
}

data object Instructions {

    fun currentEntity(local: LocationData) = Instruction(InstructionType.GET_CURRENT_ENTITY, InstructionDataLocation(local))
    fun forEachEntity(local: LocationData) = Instruction(InstructionType.FOR_EACH_ENTITY, InstructionDataLocation(local))
    fun forEachPlayer(local: LocationData) = Instruction(InstructionType.FOR_EACH_PLAYER, InstructionDataLocation(local))
    fun forEntity(local: LocationData, entityUuid: UUID) = Instruction(InstructionType.FOR_ENTITY, InstructionDataLocationUUID(local, entityUuid))
    fun forPlayer(local: LocationData, entityName: String) = Instruction(InstructionType.FOR_PLAYER, InstructionDataLocationString(local, entityName))
    fun forScoreHolders(local: LocationData, single: Boolean) = Instruction(InstructionType.FOR_SCORE_HOLDERS, InstructionDataLocationBoolean(local, single))
    fun ifNull(local: LocationData) = Instruction(InstructionType.IF_NULL, InstructionDataLocation(local))
    fun ifNotNull(local: LocationData) = Instruction(InstructionType.IF_NOT_NULL, InstructionDataLocation(local))
    fun addEntityTag(local: LocationData, tag: String) = Instruction(InstructionType.ADD_ENTITY_TAG, InstructionDataLocationString(local, tag))
    fun removeEntityTag(local: LocationData, tag: String) = Instruction(InstructionType.REMOVE_ENTITY_TAG, InstructionDataLocationString(local, tag))
    fun checkEntityTag(local: LocationData, tag: String) = Instruction(InstructionType.CHECK_ENTITY_TAG, InstructionDataLocationString(local, tag))
    fun checkNotEntityTag(local: LocationData, tag: String) = Instruction(InstructionType.CHECK_NOT_ENTITY_TAG, InstructionDataLocationString(local, tag))
    fun checkEntityTags(local: LocationData) = Instruction(InstructionType.CHECK_ENTITY_TAGS, InstructionDataLocation(local))
    fun checkNotEntityTags(local: LocationData) = Instruction(InstructionType.CHECK_NOT_ENTITY_TAGS, InstructionDataLocation(local))
    fun emptyCondition(loopId: Int) = Instruction(InstructionType.EMPTY_CONDITION, InstructionDataLoop(loopId))
    fun breakLoop(loopId: Int) = Instruction(InstructionType.BREAK_LOOP, InstructionDataLoop(loopId))
    fun continueLoop(loopId: Int) = Instruction(InstructionType.CONTINUE_LOOP, InstructionDataLoop(loopId))
    fun limiter(loopId: Int, value: Int) = Instruction(InstructionType.LIMITER, InstructionDataIntLoop(value, loopId))
    fun checkLimit(loopId: Int) = Instruction(InstructionType.CHECK_LIMIT, InstructionDataLoop(loopId))
    fun getObjective(local: LocationData, name: String) = Instruction(InstructionType.GET_OBJECTIVE, InstructionDataLocationString(local, name))
    fun getScoreboardName(local: LocationData, localStore: LocationData) = Instruction(InstructionType.GET_SCOREBOARD_NAME, InstructionDataLocationLocation(local, localStore))
    fun scoreData(objective: LocationData, name: LocationData, location: LocationData) = Instruction(InstructionType.SCORE_DATA, InstructionDataLocationLocationLocation(objective, name, location))
    fun addScore(score: LocationData, value: Int) = Instruction(InstructionType.ADD_SCORE, InstructionDataLocationInt(score, value))
    fun setScore(score: LocationData, value: Int) = Instruction(InstructionType.SET_SCORE, InstructionDataLocationInt(score, value))
    fun resetScore(objective: LocationData, name: LocationData) = Instruction(InstructionType.RESET_SCORE, InstructionDataLocationLocation(objective, name))
    fun runScoreOperation(target: LocationData, source: LocationData, operation: ScoreboardOperation, exceptionLoopId: Int) = Instruction(InstructionType.RUN_SCORE_OPERATION, InstructionDataRunScoreOperation(target, source, operation, exceptionLoopId))
    fun callFunctions(functions: List<ResourceLocation>, entity: LocationData?, world: LocationData?, position: LocationData?, rotation: LocationData?) = Instruction(InstructionType.CALL_FUNCTIONS, InstructionDataFunctionCall(functions.toList(), entity, world, position, rotation))
    fun killEntity(local: LocationData) = Instruction(InstructionType.KILL_ENTITY, InstructionDataLocation(local))
    fun checkScoreRange(score: LocationData, range: IntRangeArgument, negate: Boolean) = Instruction(InstructionType.CHECK_SCORE_RANGE, InstructionDataCheckScoreRange(score, range, negate))
    fun checkScoreCompare(target: LocationData, source: LocationData, compareOperation: ScoreboardCompareOperation) = Instruction(InstructionType.CHECK_SCORE_COMPARE, InstructionDataCheckScoreCompare(target, source, compareOperation))
    fun getEntityWorld(entity: LocationData, world: LocationData) = Instruction(InstructionType.GET_ENTITY_WORLD, InstructionDataLocationLocation(entity, world))
    fun getEntityPosition(entity: LocationData, position: LocationData) = Instruction(InstructionType.GET_ENTITY_POSITION, InstructionDataLocationLocation(entity, position))
    fun getEntityRotation(entity: LocationData, rotation: LocationData) = Instruction(InstructionType.GET_ENTITY_ROTATION, InstructionDataLocationLocation(entity, rotation))
    fun applyPositionWorld(positionFrom: LocationData, positionNew: LocationData, worldPosition: WorldCoordinates) = Instruction(InstructionType.APPLY_POSITION_WORLD, InstructionDataLocationLocationWorldCoordinates(positionFrom, positionNew, worldPosition))

}

enum class InstructionType {

    GET_CURRENT_ENTITY,
    FOR_EACH_ENTITY,
    FOR_EACH_PLAYER,
    FOR_ENTITY,
    FOR_PLAYER,
    IF_NULL,
    IF_NOT_NULL,
    ADD_ENTITY_TAG,
    REMOVE_ENTITY_TAG,
    CHECK_ENTITY_TAG,
    CHECK_NOT_ENTITY_TAG,
    CHECK_ENTITY_TAGS,
    CHECK_NOT_ENTITY_TAGS,
    EMPTY_CONDITION,
    BREAK_LOOP,
    CONTINUE_LOOP,
    LIMITER,
    CHECK_LIMIT,
    GET_OBJECTIVE,
    GET_SCOREBOARD_NAME,
    SCORE_DATA,
    ADD_SCORE,
    SET_SCORE,
    RESET_SCORE,
    RUN_SCORE_OPERATION,
    CALL_FUNCTIONS,
    FOR_SCORE_HOLDERS,
    KILL_ENTITY,
    CHECK_SCORE_RANGE,
    CHECK_SCORE_COMPARE,
    GET_ENTITY_WORLD,
    GET_ENTITY_POSITION,
    GET_ENTITY_ROTATION,
    APPLY_POSITION_WORLD,

}
