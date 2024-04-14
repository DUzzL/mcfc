package dev.epicpix.minecraftfunctioncompiler.compiler.impl

import dev.epicpix.minecraftfunctioncompiler.compiler.ICompilationContext
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import dev.epicpix.minecraftfunctioncompiler.il.*
import java.util.*

data class CompilationContext(
    override val highestLocal: Int = 0,
    override val currentEntity: LocationData? = null,
    override val currentWorld: LocationData? = null,
    override val currentPosition: LocationData? = null,
    override val currentRotation: LocationData? = null,
    override val addInstruction: (Instruction) -> Unit,
) : ICompilationContext {

    override fun with(
        highestLocal: Int?,
        currentEntity: LocationData?,
        currentWorld: LocationData?,
        currentPosition: LocationData?,
        currentRotation: LocationData?,
        addInstruction: ((Instruction) -> Unit)?,
    ): ICompilationContext {
        return copy(highestLocal = highestLocal ?: this.highestLocal, currentEntity = currentEntity ?: this.currentEntity, currentWorld = currentWorld ?: this.currentWorld, currentPosition = currentPosition ?: this.currentPosition, currentRotation = currentRotation ?: this.currentRotation, addInstruction = addInstruction ?: this.addInstruction)
    }

    override fun withResultStorage(requireResult: Boolean, requireSuccess: Boolean, exec: ICompilationContext.(ResultStorage) -> Unit) {
        if(requireResult == requireSuccess) throw UnsupportedOperationException("calling withResultStorage() with requireResult == requireSuccess is not supported")
        val loc = LocationData(local = highestLocal)
        val res = if(requireResult) ResultStorage(result = loc) else ResultStorage(success = loc)
        val instr = Instructions2.createResultStorage(loc)
        addInstruction(instr)
        with(highestLocal = highestLocal + 1, addInstruction = instr.children::add).exec(res)
    }

    override fun getScoreData(objective: LocationData, name: LocationData, autoCreate: Boolean, exec: ICompilationContext.(scoreData: LocationData) -> Unit) {
        val scoreData = LocationData(local = highestLocal)
        val instruction = Instructions.scoreData(objective, name, scoreData, autoCreate)
        addInstruction(instruction)
        if(autoCreate) {
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scoreData)
        }else {
            val nonNull = Instructions2.ifNotNull(scoreData)
            instruction.with(nonNull)
            exec(with(highestLocal = highestLocal + 1, addInstruction = nonNull.children::add), scoreData)
        }
    }

    override fun forPosition(pos: WorldCoordinates, exec: ICompilationContext.(positionId: LocationData) -> Unit) {
        getPosition { basePositionId ->
            val positionId = LocationData(local = highestLocal)
            val instruction = Instructions.applyPositionWorld(basePositionId, positionId, pos)
            addInstruction(instruction)
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), positionId)
        }
    }

    override fun getWorld(exec: ICompilationContext.(worldId: LocationData) -> Unit) {
        if(currentWorld != null) {
            exec(currentWorld)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity)
                val instruction = Instructions.getEntityWorld(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(with(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    override fun getPosition(exec: ICompilationContext.(positionId: LocationData) -> Unit) {
        if(currentPosition != null) {
            exec(currentPosition)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity)
                val instruction = Instructions.getEntityPosition(entity, local)
                nullCheck.with(instruction)
                addInstruction(nullCheck)
                exec(with(currentPosition = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    override fun getRotation(exec: ICompilationContext.(rotationId: LocationData) -> Unit) {
        if(currentRotation != null) {
            exec(currentRotation)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity)
                val instruction = Instructions.getEntityRotation(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(with(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    override fun forScope(exec: ICompilationContext.(scope: ScopeData) -> Unit) {
        val scope = ScopeData(highestLocal)
        val instruction = Instructions2.scope(scope)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scope)
    }

    override fun getObjective(name: String, exec: ICompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions2.ifNotNull(local)
        addInstruction(Instructions.getObjective(local, name).with(instruction))
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    override fun forScoreHolder(holder: ScoreHolderArgument, exec: ICompilationContext.(local: LocationData) -> Unit) {
        if(holder.all) {
            forEachScoreHolder(single = holder.single, exec)
        }else if(holder.name != null) {
            exec(LocationData(string = holder.name))
        }else if(holder.selector != null) {
            val selector = if(holder.single) holder.selector.copy(limit = 1) else holder.selector
            forSelector(selector) { entity, _, _ ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getScoreboardName(entity, local)
                addInstruction(Instructions2.ifNotNull(entity).with(instruction))
                exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    override fun getScoreboardName(entity: LocationData, exec: ICompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.getScoreboardName(entity, local)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    override fun forCurrentEntity(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        if(currentEntity != null) {
            forScope { scope ->
                exec(this, currentEntity!!, scope, scope)
            }
        }else {
            forScope { scope ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions2.getCurrentEntity(local)
                addInstruction(instruction)
                exec(with(currentEntity = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, scope, scope)
            }
        }
    }

    override fun forEachEntity(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEachEntity(local)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    override fun forEachPlayer(exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions2.forEachPlayer(local)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    override fun forEntity(entityUuid: UUID, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forEntity(local, entityUuid)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    override fun forPlayer(playerName: String, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { scope ->
            val local = LocationData(local = highestLocal)
            val instruction = Instructions.forPlayer(local, playerName)
            addInstruction(instruction)
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, scope, scope)
        }
    }

    override fun forEachScoreHolder(single: Boolean, exec: ICompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.forScoreHolders(local, single)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    override fun forSelector(selector: SelectorArgument, exec: ICompilationContext.(local: LocationData, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        var limiterScope: ScopeData? = null
        var limiterCounter: LocationData? = null
        fun checkEntityCondition(context: ICompilationContext, local: LocationData, breakScope: ScopeData, continueScope: ScopeData) {
            var ctx = context
            val nonNull = Instructions2.ifNotNull(local)
            val stacker = InstructionStacker(nonNull)
            if(selector.entityTypes != null) {
                stacker.stackInstruction(Instructions.checkEntityTypes(local, selector.entityTypes.data, selector.entityTypes.negated))
            }
            if(selector.gamemode != null) {
                stacker.stackInstruction(Instructions.checkGamemode(local, selector.gamemode.data, selector.gamemode.negated))
            }
            for(tag in selector.tags) {
                stacker.stackInstruction(Instructions2.checkEntityTag(local, tag.data, tag.negated))
            }
            if(selector.hasTags != null) {
                stacker.stackInstruction(Instructions2.checkEntityTags(local, selector.hasTags))
            }
            if(selector.yaw != null || selector.pitch != null) {
                val rotationLocal = LocationData(local = context.highestLocal)
                stacker.stackInstruction(Instructions.getEntityRotation(local, rotationLocal))
                stacker.stackInstruction(Instructions.checkRotation(rotationLocal, selector.yaw, selector.pitch))
                ctx = ctx.with(highestLocal = context.highestLocal + 1)
            }
            if(selector.team != null) {
                stacker.stackInstruction(Instructions.checkTeam(local, selector.team.data, selector.team.negated))
            }
            if(selector.hasTeam != null) {
                stacker.stackInstruction(Instructions.hasTeam(local, selector.hasTeam))
            }
            if(selector.scores.isNotEmpty()) {
                for((obj, range) in selector.scores) {
                    ctx.with(addInstruction = stacker::stackInstruction).getObjective(obj) { objective ->
                        getScoreboardName(local) { name ->
                            getScoreData(objective, name, false) { score ->
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
            exec(ctx.with(addInstruction = execBase.children::add), local, breakScope, continueScope)
            if(selector.limit == 1) {
                execBase.with(Instructions2.exitScope(breakScope))
            }else if(selector.limit != null) {
                execBase.with(Instructions2.checkLimit(limiterScope!!, limiterCounter!!))
            }
            context.addInstruction(stacker.base)
        }

        val realContext = if(selector.limit != 1 && selector.limit != null) {
            limiterScope = ScopeData(highestLocal)
            limiterCounter = LocationData(highestLocal + 1)
            val instr = Instructions2.limiter(limiterScope, limiterCounter, selector.limit)
            val newContext = with(highestLocal = highestLocal + 2, addInstruction = instr.children::add)
            addInstruction(instr)
            newContext
        } else this

        if(selector.playerName != null) {
            realContext.forPlayer(selector.playerName) { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else if(selector.entityUuid != null) {
            realContext.forEntity(selector.entityUuid) { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else if(selector.self) {
            realContext.forCurrentEntity { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else if(selector.selectPlayers) {
            realContext.forEachPlayer { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else {
            realContext.forEachEntity { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
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