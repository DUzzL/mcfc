package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.Instructions
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import java.util.*

data class CompilationContext(
    val highestLocal: Int = 0,
    val currentEntity: LocationData? = null,
    val currentWorld: LocationData? = null,
    val currentPosition: LocationData? = null,
    val currentRotation: LocationData? = null,
    val addInstruction: (Instruction) -> Unit
) {

    fun getScoreData(objective: LocationData, name: LocationData, exec: CompilationContext.(scoreData: LocationData) -> Unit) {
        val scoreData = LocationData(local = highestLocal)
        val instruction = Instructions.scoreData(objective, name, scoreData)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scoreData)
        addInstruction(instruction)
    }

    fun forPosition(pos: WorldCoordinates, exec: CompilationContext.(positionId: LocationData) -> Unit) {
        getPosition { basePositionId ->
            val positionId = LocationData(local = highestLocal)
            val instruction = Instructions.applyPositionWorld(basePositionId, positionId, pos)
            exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), positionId)
            addInstruction(instruction)
        }
    }

    fun getWorld(exec: CompilationContext.(worldId: LocationData) -> Unit) {
        if(currentWorld != null) {
            exec(currentWorld)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getEntityWorld(entity, local)
                exec(copy(currentEntity = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
                addInstruction(instruction)
            }
        }
    }

    fun getPosition(exec: CompilationContext.(positionId: LocationData) -> Unit) {
        if(currentPosition != null) {
            exec(currentPosition)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getEntityPosition(entity, local)
                exec(copy(currentPosition = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
                addInstruction(instruction)
            }
        }
    }

    fun getRotation(exec: CompilationContext.(rotationId: LocationData) -> Unit) {
        if(currentRotation != null) {
            exec(currentRotation)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getEntityRotation(entity, local)
                exec(copy(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
                addInstruction(instruction)
            }
        }
    }

    fun forEmptyCondition(exec: CompilationContext.(loopId: Int) -> Unit) {
        val loopId = highestLocal
        val instruction = Instructions.emptyCondition(loopId)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), loopId)
        addInstruction(instruction)
    }

    fun getObjective(name: String, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.ifNotNull(local)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
        addInstruction(Instructions.getObjective(local, name).with(instruction))
    }

    fun forScoreHolder(holder: ScoreHolderArgument, exec: CompilationContext.(local: LocationData) -> Unit) {
        if(holder.all) {
            forEachScoreHolder(single = false, exec)
        }else if(holder.name != null) {
            exec(LocationData(string = holder.name))
        }else if(holder.selector != null) {
            forSelector(holder.selector) { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getScoreboardName(entity, local)
                exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
                addInstruction(Instructions.ifNotNull(entity).with(instruction))
            }
        }
    }

    fun forCurrentEntity(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        if(currentEntity != null) {
            forEmptyCondition { loopId ->
                exec(this, currentEntity!!, loopId, loopId)
            }
        }else {
            forEmptyCondition { loopId ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.currentEntity(local)
                exec(copy(currentEntity = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, loopId, loopId)
                addInstruction(instruction)
            }
        }
    }

    fun forEachEntity(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEachEntity(local)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
            addInstruction(instruction)
        }
    }

    fun forEachPlayer(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEachPlayer(local)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
            addInstruction(instruction)
        }
    }

    fun forEntity(entityUuid: UUID, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEntity(local, entityUuid)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
            addInstruction(instruction)
        }
    }

    fun forPlayer(playerName: String, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { loopId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forPlayer(local, playerName)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).exec(local, loopId, loopId)
            addInstruction(instruction)
        }
    }

    fun forEachScoreHolder(single: Boolean = false, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.forScoreHolders(local, single)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
        addInstruction(instruction)
    }

    fun forSelector(selector: SelectorArgument, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        var limiterId: Int? = null
        fun checkEntityCondition(context: CompilationContext, local: LocationData, breakId: Int, continueId: Int) {
            val nonNull = Instructions.ifNotNull(local)
            for(tag in selector.tags) {
                if(!tag.negated) nonNull.with(Instructions.checkEntityTag(local, tag.data, continueId))
                else nonNull.with(Instructions.checkNotEntityTag(local, tag.data, continueId))
            }
            if(selector.hasTags != null) {
                if(selector.hasTags) nonNull.with(Instructions.checkEntityTags(local, continueId))
                else nonNull.with(Instructions.checkNotEntityTags(local, continueId))
            }
            exec(context.copy(addInstruction = nonNull.children::add), local, breakId, continueId)
            if(selector.limit == 1) {
                nonNull.with(Instructions.breakLoop(breakId))
            }else if(selector.limit != null) {
                nonNull.with(Instructions.checkLimit(limiterId!!))
            }
            context.addInstruction(nonNull)
        }

        val realContext = if(selector.limit != 1 && selector.limit != null) {
            limiterId = highestLocal
            val instr = Instructions.limiter(limiterId, selector.limit)
            val newContext = copy(highestLocal = highestLocal + 1, addInstruction = instr.children::add)
            addInstruction(instr)
            newContext
        } else this

        if(selector.playerName != null) {
            realContext.forPlayer(selector.playerName) { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }else if(selector.entityUuid != null) {
            realContext.forEntity(selector.entityUuid) { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }else if(selector.self) {
            realContext.forCurrentEntity { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }else if(selector.entityType == "minecraft:player") {
            realContext.forEachPlayer { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }else {
            realContext.forEachEntity { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }
    }

}