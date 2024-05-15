package dev.epicpix.minecraftfunctioncompiler.il

import dev.epicpix.annotations.InstructionDestination
import dev.epicpix.annotations.InstructionSource
import dev.epicpix.annotations.InstructionSourceClass
import dev.epicpix.minecraftfunctioncompiler.data.*

@Suppress("unused")
@InstructionSourceClass("dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes", "dev.epicpix.minecraftfunctioncompiler.il.Instructions")
private interface InstructionDefinitions {
    // Minecraft specific instructions
    fun scoreData(@InstructionSource objective: LocationData, @InstructionSource name: LocationData, @InstructionDestination score: LocationData, autoCreate: Boolean): Instruction
    fun resetScore(@InstructionSource objective: LocationData, @InstructionSource name: LocationData): Instruction
    fun resetPlayerScore(@InstructionSource name: LocationData): Instruction
    fun runScoreOperation(@InstructionSource target: LocationData, @InstructionSource source: LocationData, operation: ScoreboardOperation, exceptionScope: ScopeData): Instruction
    fun callFunctions(functions: List<DataLocation>, @InstructionSource entity: LocationData?, @InstructionSource world: LocationData?, @InstructionSource position: LocationData?, @InstructionSource rotation: LocationData?): Instruction
    fun checkScoreRange(@InstructionSource score: LocationData, range: IntRangeArgument, negate: Boolean): Instruction
    fun checkScoreCompare(@InstructionSource target: LocationData, @InstructionSource source: LocationData, compareOperation: ScoreboardCompareOperation): Instruction
    fun getEntityWorld(@InstructionSource entity: LocationData, @InstructionDestination world: LocationData): Instruction
    fun applyPositionWorld(@InstructionSource from: LocationData, @InstructionDestination to: LocationData, position: WorldCoordinates): Instruction
    fun checkEntityTypes(@InstructionSource entity: LocationData, types: List<DataLocation>, negated: Boolean): Instruction
    fun alignPosition(@InstructionSource position: LocationData, @InstructionDestination result: LocationData, swizzle: SwizzleArgument): Instruction
    fun checkGamemode(@InstructionSource entity: LocationData, gamemode: Gamemode, negated: Boolean): Instruction
    fun setGamemode(@InstructionSource entity: LocationData, gamemode: Gamemode): Instruction
    fun checkTeam(@InstructionSource entity: LocationData, team: String, negated: Boolean): Instruction
    fun hasTeam(@InstructionSource entity: LocationData, negated: Boolean): Instruction
    fun checkRotation(@InstructionSource rotation: LocationData, yaw: FloatRangeArgument?, pitch: FloatRangeArgument?): Instruction
}