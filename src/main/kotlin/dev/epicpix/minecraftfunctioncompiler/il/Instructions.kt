package dev.epicpix.minecraftfunctioncompiler.il

import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.GameType
import java.util.*

data class LocationData(
    val local: Int? = null,
    val string: String? = null,

    val isSource: Boolean = false,
    val isDestination: Boolean = false,
) {
    fun source() = LocationData(local, string, isSource = true, isDestination = false)
    fun destination() = LocationData(local, string, isSource = false, isDestination = true)
}

enum class ScoreboardOperation { SET, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, MIN, MAX, SWAP }
enum class ScoreboardCompareOperation { LT, LE, EQ, NE, GE, GT }

data class Instruction(val type: InstructionType, val data: Map<String, Any?>, val children: MutableList<Instruction> = mutableListOf()) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T = data[key] as T

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

fun remapInstructionData(data: Map<String, Any?>, mappings: Map<Int, Int>): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    data.forEach { (key, value) ->
        if(value is LocationData) {
            if(value.local != null) {
                result[key] = LocationData(mappings[value.local] ?: value.local, value.string, value.isSource, value.isDestination)
                return@forEach
            }
        }
        result[key] = value
    }
    return result
}

data object Instructions {

    fun currentEntity(entity: LocationData) = Instruction(InstructionType.GET_CURRENT_ENTITY, mapOf("entity" to entity.destination()))
    fun forEachEntity(entity: LocationData) = Instruction(InstructionType.FOR_EACH_ENTITY, mapOf("entity" to entity.destination()))
    fun forEachPlayer(entity: LocationData) = Instruction(InstructionType.FOR_EACH_PLAYER, mapOf("entity" to entity.destination()))
    fun forEntity(entity: LocationData, entityUuid: UUID) = Instruction(InstructionType.FOR_ENTITY, mapOf("entity" to entity.destination(), "uuid" to entityUuid))
    fun forPlayer(entity: LocationData, entityName: String) = Instruction(InstructionType.FOR_PLAYER, mapOf("entity" to entity.destination(), "name" to entityName))
    fun forScoreHolders(local: LocationData, single: Boolean) = Instruction(InstructionType.FOR_SCORE_HOLDERS, mapOf("local" to local.destination(), "single" to single))
    fun ifNotNull(local: LocationData) = Instruction(InstructionType.IF_NOT_NULL, mapOf("local" to local.source()))
    fun addEntityTag(entity: LocationData, tag: String) = Instruction(InstructionType.ADD_ENTITY_TAG, mapOf("entity" to entity.source(), "tag" to tag))
    fun removeEntityTag(entity: LocationData, tag: String) = Instruction(InstructionType.REMOVE_ENTITY_TAG, mapOf("entity" to entity.source(), "tag" to tag))
    fun checkEntityTag(entity: LocationData, tag: String, negated: Boolean) = Instruction(InstructionType.CHECK_ENTITY_TAG, mapOf("entity" to entity.source(), "tag" to tag, "negated" to negated))
    fun checkEntityTags(entity: LocationData, negated: Boolean) = Instruction(InstructionType.CHECK_ENTITY_TAGS, mapOf("entity" to entity.source(), "negated" to negated))
    fun scope(scopeId: Int) = Instruction(InstructionType.SCOPE, mapOf("scope" to scopeId))
    fun exitScope(scopeId: Int) = Instruction(InstructionType.EXIT_SCOPE, mapOf("scope" to scopeId))
    fun limiter(scopeId: Int, value: Int) = Instruction(InstructionType.LIMITER, mapOf("scope" to scopeId, "limit" to value))
    fun checkLimit(scopeId: Int) = Instruction(InstructionType.CHECK_LIMIT, mapOf("scope" to scopeId))
    fun getObjective(objective: LocationData, name: String) = Instruction(InstructionType.GET_OBJECTIVE, mapOf("objective" to objective.destination(), "name" to name))
    fun getScoreboardName(entity: LocationData, name: LocationData) = Instruction(InstructionType.GET_SCOREBOARD_NAME, mapOf("entity" to entity.source(), "name" to name.destination()))
    fun scoreData(objective: LocationData, name: LocationData, location: LocationData) = Instruction(InstructionType.SCORE_DATA, mapOf("objective" to objective.source(), "name" to name.source(), "score" to location.destination()))
    fun addScore(score: LocationData, value: Int) = Instruction(InstructionType.ADD_SCORE, mapOf("score" to score.source(), "value" to value))
    fun setScore(score: LocationData, value: Int) = Instruction(InstructionType.SET_SCORE, mapOf("score" to score.source(), "value" to value))
    fun resetScore(objective: LocationData, name: LocationData) = Instruction(InstructionType.RESET_SCORE, mapOf("objective" to objective.source(), "name" to name.source()))
    fun runScoreOperation(target: LocationData, source: LocationData, operation: ScoreboardOperation, exceptionScope: Int) = Instruction(InstructionType.RUN_SCORE_OPERATION, mapOf("target" to target.source(), "source" to source.source(), "operation" to operation, "exceptionScope" to exceptionScope))
    fun callFunctions(functions: List<ResourceLocation>, entity: LocationData?, world: LocationData?, position: LocationData?, rotation: LocationData?) = Instruction(InstructionType.CALL_FUNCTIONS, mapOf("functions" to functions.toList(), "entity" to entity?.source(), "world" to world?.source(), "position" to position?.source(), "rotation" to rotation?.source()))
    fun killEntity(entity: LocationData) = Instruction(InstructionType.KILL_ENTITY, mapOf("entity" to entity))
    fun checkScoreRange(score: LocationData, range: IntRangeArgument, negate: Boolean) = Instruction(InstructionType.CHECK_SCORE_RANGE, mapOf("score" to score.source(), "range" to range, "negate" to negate))
    fun checkScoreCompare(target: LocationData, source: LocationData, compareOperation: ScoreboardCompareOperation) = Instruction(InstructionType.CHECK_SCORE_COMPARE, mapOf("target" to target.source(), "source" to source.source(), "compareOperation" to compareOperation))
    fun getEntityWorld(entity: LocationData, world: LocationData) = Instruction(InstructionType.GET_ENTITY_WORLD, mapOf("entity" to entity.source(), "world" to world.destination()))
    fun getEntityPosition(entity: LocationData, position: LocationData) = Instruction(InstructionType.GET_ENTITY_POSITION, mapOf("entity" to entity.source(), "position" to position.destination()))
    fun getEntityRotation(entity: LocationData, rotation: LocationData) = Instruction(InstructionType.GET_ENTITY_ROTATION, mapOf("entity" to entity.source(), "rotation" to rotation.destination()))
    fun applyPositionWorld(positionFrom: LocationData, positionNew: LocationData, worldPosition: WorldCoordinates) = Instruction(InstructionType.APPLY_POSITION_WORLD, mapOf("from" to positionFrom.source(), "to" to positionNew.destination(), "position" to worldPosition))
    fun checkEntityTypes(entity: LocationData, types: List<ResourceLocation>, negated: Boolean) = Instruction(InstructionType.CHECK_ENTITY_TYPES, mapOf("entity" to entity.source(), "types" to types.toList(), "negated" to negated))
    fun alignPosition(position: LocationData, result: LocationData, swizzle: SwizzleArgument) = Instruction(InstructionType.ALIGN_POSITION, mapOf("position" to position.source(), "result" to result.destination(), "swizzle" to swizzle))
    fun checkGamemode(entity: LocationData, gamemode: GameType, negated: Boolean) = Instruction(InstructionType.CHECK_GAMEMODE, mapOf("entity" to entity.source(), "gamemode" to gamemode, "negated" to negated))
    fun setGamemode(entity: LocationData, gamemode: GameType) = Instruction(InstructionType.SET_GAMEMODE, mapOf("entity" to entity.source(), "gamemode" to gamemode))
    fun checkTeam(entity: LocationData, team: String, negated: Boolean) = Instruction(InstructionType.CHECK_TEAM, mapOf("entity" to entity.source(), "team" to team, "negated" to negated))
    fun hasTeam(entity: LocationData, negated: Boolean) = Instruction(InstructionType.HAS_TEAM, mapOf("entity" to entity.source(), "negated" to negated))
}

enum class InstructionType {

    GET_CURRENT_ENTITY,
    FOR_EACH_ENTITY,
    FOR_EACH_PLAYER,
    FOR_ENTITY,
    FOR_PLAYER,
    IF_NOT_NULL,
    ADD_ENTITY_TAG,
    REMOVE_ENTITY_TAG,
    CHECK_ENTITY_TAG,
    CHECK_ENTITY_TAGS,
    SCOPE,
    EXIT_SCOPE,
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
    CHECK_ENTITY_TYPES,
    ALIGN_POSITION,
    CHECK_GAMEMODE,
    SET_GAMEMODE,
    CHECK_TEAM,
    HAS_TEAM,
}
