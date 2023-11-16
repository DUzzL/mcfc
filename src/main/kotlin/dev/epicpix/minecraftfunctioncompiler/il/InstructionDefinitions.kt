package dev.epicpix.minecraftfunctioncompiler.il

import dev.epicpix.annotations.InstructionDestination
import dev.epicpix.annotations.InstructionSource
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.GameType
import java.util.*

@Suppress("unused")
private interface InstructionDefinitions {
    fun getCurrentEntity(@InstructionDestination entity: LocationData): Instruction
    fun forEachEntity(@InstructionDestination entity: LocationData): Instruction
    fun forEachPlayer(@InstructionDestination entity: LocationData): Instruction
    fun forEntity(@InstructionDestination entity: LocationData, uuid: UUID): Instruction
    fun forPlayer(@InstructionDestination entity: LocationData, name: String): Instruction
    fun forScoreHolders(@InstructionDestination local: LocationData, single: Boolean): Instruction
    fun ifNotNull(@InstructionSource local: LocationData): Instruction
    fun addEntityTag(@InstructionSource entity: LocationData, tag: String): Instruction
    fun removeEntityTag(@InstructionSource entity: LocationData, tag: String): Instruction
    fun checkEntityTag(@InstructionSource entity: LocationData, tag: String, negated: Boolean): Instruction
    fun checkEntityTags(@InstructionSource entity: LocationData, negated: Boolean): Instruction
    fun scope(scope: Int): Instruction
    fun exitScope(scope: Int): Instruction
    fun limiter(scope: Int, limit: Int): Instruction
    fun checkLimit(scope: Int): Instruction
    fun getObjective(@InstructionDestination objective: LocationData, name: String): Instruction
    fun getScoreboardName(@InstructionSource entity: LocationData, @InstructionDestination name: LocationData): Instruction
    fun scoreData(@InstructionSource objective: LocationData, @InstructionSource name: LocationData, @InstructionDestination score: LocationData, autoCreate: Boolean): Instruction
    fun addScore(@InstructionSource score: LocationData, value: Int): Instruction
    fun setScore(@InstructionSource score: LocationData, value: Int): Instruction
    fun resetScore(@InstructionSource objective: LocationData, @InstructionSource name: LocationData): Instruction
    fun runScoreOperation(@InstructionSource target: LocationData, @InstructionSource source: LocationData, operation: ScoreboardOperation, exceptionScope: Int): Instruction
    fun callFunctions(functions: List<ResourceLocation>, @InstructionSource entity: LocationData?, @InstructionSource world: LocationData?, @InstructionSource position: LocationData?, @InstructionSource rotation: LocationData?): Instruction
    fun killEntity(@InstructionSource entity: LocationData): Instruction
    fun checkScoreRange(@InstructionSource score: LocationData, range: IntRangeArgument, negate: Boolean): Instruction
    fun checkScoreCompare(@InstructionSource target: LocationData, @InstructionSource source: LocationData, compareOperation: ScoreboardCompareOperation): Instruction
    fun getEntityWorld(@InstructionSource entity: LocationData, @InstructionDestination world: LocationData): Instruction
    fun getEntityPosition(@InstructionSource entity: LocationData, @InstructionDestination position: LocationData): Instruction
    fun getEntityRotation(@InstructionSource entity: LocationData, @InstructionDestination rotation: LocationData): Instruction
    fun applyPositionWorld(@InstructionSource from: LocationData, @InstructionDestination to: LocationData, position: WorldCoordinates): Instruction
    fun checkEntityTypes(@InstructionSource entity: LocationData, types: List<ResourceLocation>, negated: Boolean): Instruction
    fun alignPosition(@InstructionSource position: LocationData, @InstructionDestination result: LocationData, swizzle: SwizzleArgument): Instruction
    fun checkGamemode(@InstructionSource entity: LocationData, gamemode: GameType, negated: Boolean): Instruction
    fun setGamemode(@InstructionSource entity: LocationData, gamemode: GameType): Instruction
    fun checkTeam(@InstructionSource entity: LocationData, team: String, negated: Boolean): Instruction
    fun hasTeam(@InstructionSource entity: LocationData, negated: Boolean): Instruction
    fun getAttributeInstance(@InstructionSource entity: LocationData, @InstructionDestination instance: LocationData, attribute: ResourceLocation): Instruction
    fun setAttributeBase(@InstructionSource instance: LocationData, value: Double): Instruction
}