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
        addInstruction(instruction)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scoreData)
    }

    fun forPosition(pos: WorldCoordinates, exec: CompilationContext.(positionId: LocationData) -> Unit) {
        getPosition { basePositionId ->
            val positionId = LocationData(local = highestLocal)
            val instruction = Instructions.applyPositionWorld(basePositionId, positionId, pos)
            addInstruction(instruction)
            exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), positionId)
        }
    }

    fun getWorld(exec: CompilationContext.(worldId: LocationData) -> Unit) {
        if(currentWorld != null) {
            exec(currentWorld)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions.ifNotNull(entity)
                val instruction = Instructions.getEntityWorld(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(copy(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getPosition(exec: CompilationContext.(positionId: LocationData) -> Unit) {
        if(currentPosition != null) {
            exec(currentPosition)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions.ifNotNull(entity)
                val instruction = Instructions.getEntityPosition(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(copy(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getRotation(exec: CompilationContext.(rotationId: LocationData) -> Unit) {
        if(currentRotation != null) {
            exec(currentRotation)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions.ifNotNull(entity)
                val instruction = Instructions.getEntityRotation(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(copy(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun forEmptyCondition(exec: CompilationContext.(loopId: Int) -> Unit) {
        val loopId = highestLocal
        val instruction = Instructions.scope(loopId)
        addInstruction(instruction)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), loopId)
    }

    fun getObjective(name: String, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.ifNotNull(local)
        addInstruction(Instructions.getObjective(local, name).with(instruction))
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
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
                addInstruction(Instructions.ifNotNull(entity).with(instruction))
                exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getScoreboardName(entity: LocationData, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.getScoreboardName(entity, local)
        addInstruction(instruction)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    fun forCurrentEntity(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        if(currentEntity != null) {
            forEmptyCondition { loopId ->
                exec(this, currentEntity!!, loopId, loopId)
            }
        }else {
            forEmptyCondition { loopId ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getCurrentEntity(local)
                addInstruction(instruction)
                exec(copy(currentEntity = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, loopId, loopId)
            }
        }
    }

    fun forEachEntity(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEachEntity(local)
            addInstruction(instruction)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
        }
    }

    fun forEachPlayer(exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEachPlayer(local)
            addInstruction(instruction)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
        }
    }

    fun forEntity(entityUuid: UUID, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { breakId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEntity(local, entityUuid)
            addInstruction(instruction)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forEmptyCondition { continueId ->
                exec(local, breakId, continueId)
            }
        }
    }

    fun forPlayer(playerName: String, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        forEmptyCondition { loopId ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forPlayer(local, playerName)
            addInstruction(instruction)
            copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).exec(local, loopId, loopId)
        }
    }

    fun forEachScoreHolder(single: Boolean = false, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.forScoreHolders(local, single)
        addInstruction(instruction)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    fun forSelector(selector: SelectorArgument, exec: CompilationContext.(local: LocationData, breakId: Int, continueId: Int) -> Unit) {
        var limiterId: Int? = null
        fun checkEntityCondition(context: CompilationContext, local: LocationData, breakId: Int, continueId: Int) {
            var ctx = context
            val nonNull = Instructions.ifNotNull(local)
            val stacker = InstructionStacker(nonNull)
            if(selector.entityTypes != null) {
                stacker.stackInstruction(Instructions.checkEntityTypes(local, selector.entityTypes.data, selector.entityTypes.negated))
            }
            if(selector.gamemode != null) {
                stacker.stackInstruction(Instructions.checkGamemode(local, selector.gamemode.data, selector.gamemode.negated))
            }
            for(tag in selector.tags) {
                stacker.stackInstruction(Instructions.checkEntityTag(local, tag.data, tag.negated))
            }
            if(selector.hasTags != null) {
                stacker.stackInstruction(Instructions.checkEntityTags(local, selector.hasTags))
            }
            if(selector.team != null) {
                stacker.stackInstruction(Instructions.checkTeam(local, selector.team.data, selector.team.negated))
            }
            if(selector.hasTeam != null) {
                stacker.stackInstruction(Instructions.hasTeam(local, selector.hasTeam))
            }
            if(selector.scores.isNotEmpty()) {
                for((obj, range) in selector.scores) {
                    ctx.copy(addInstruction = stacker::stackInstruction).getObjective(obj) { objective ->
                        getScoreboardName(local) { name ->
                            getScoreData(objective, name) { score ->
                                val instr = Instructions.checkScoreRange(score, range, false)
                                addInstruction(instr)
                                stacker.current = instr
                                ctx = this
                            }
                        }
                    }
                }
            }
            val execBase = stacker.current
            exec(ctx.copy(addInstruction = execBase.children::add), local, breakId, continueId)
            if(selector.limit == 1) {
                execBase.with(Instructions.exitScope(breakId))
            }else if(selector.limit != null) {
                execBase.with(Instructions.checkLimit(limiterId!!))
            }
            context.addInstruction(stacker.base)
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
        }else if(selector.selectPlayers) {
            realContext.forEachPlayer { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }else {
            realContext.forEachEntity { local, breakId, continueId -> checkEntityCondition(this, local, breakId, continueId) }
        }
    }

}

private class InstructionStacker(
    val base: Instruction,
    var current: Instruction = base,
) {
    fun stackInstruction(instruction: Instruction) {
        current.with(instruction)
        current = instruction
    }
}