package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.compiler.impl.ResultStorage
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData
import java.util.*

interface ICompilationContext {
    val highestLocal: Int
    val currentEntity: LocationData?
    val currentWorld: LocationData?
    val currentPosition: LocationData?
    val currentRotation: LocationData?
    val addInstruction: (Instruction) -> Unit

    fun with(highestLocal: Int? = null, currentEntity: LocationData? = null, currentWorld: LocationData? = null, currentPosition: LocationData? = null, currentRotation: LocationData? = null, addInstruction: ((Instruction) -> Unit)? = null): ICompilationContext
    fun withResultStorage(requireResult: Boolean = false, requireSuccess: Boolean = false, exec: ICompilationContext.(ResultStorage) -> Unit)

    fun getScoreData(objective: LocationData, name: LocationData, autoCreate: Boolean, exec: ICompilationContext.(scoreData: LocationData) -> Unit)
    fun forPosition(pos: WorldCoordinates, exec: ICompilationContext.(positionId: LocationData) -> Unit)
    fun getWorld(exec: ICompilationContext.(worldId: LocationData) -> Unit)
    fun getPosition(exec: ICompilationContext.(positionId: LocationData) -> Unit)
    fun getRotation(exec: ICompilationContext.(rotationId: LocationData) -> Unit)
    fun forScope(exec: ICompilationContext.(scope: ScopeData) -> Unit)
    fun getObjective(name: String, exec: ICompilationContext.(local: LocationData) -> Unit)
    fun forScoreHolder(holder: ScoreHolderArgument, exec: ICompilationContext.(local: LocationData) -> Unit)
    fun getScoreboardName(entity: LocationData, exec: ICompilationContext.(local: LocationData) -> Unit)
    fun forCurrentEntity(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
    fun forEachEntity(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
    fun forEachPlayer(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
    fun forEntity(entityUuid: UUID, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
    fun forPlayer(playerName: String, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
    fun forEachScoreHolder(single: Boolean = false, exec: ICompilationContext.(local: LocationData) -> Unit)
    fun forSelector(selector: SelectorArgument, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit)
}