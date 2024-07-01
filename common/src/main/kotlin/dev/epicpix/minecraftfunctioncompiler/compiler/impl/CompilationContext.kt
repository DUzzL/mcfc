package dev.epicpix.minecraftfunctioncompiler.compiler.impl

import dev.epicpix.minecraftfunctioncompiler.compiler.ResultStorage
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates
import dev.epicpix.minecraftfunctioncompiler.data.locations.*
import dev.epicpix.minecraftfunctioncompiler.il.*
import java.util.*

data class CompilationContext(
    val highestLocal: Int = 0,
    val currentEntity: LocationData<Entity>? = null,
    val currentWorld: LocationData<World>? = null,
    val currentPosition: LocationData<Vec3>? = null,
    val currentRotation: LocationData<Vec2>? = null,
    val addInstruction: (Instruction) -> Unit,
) {

    fun with(
        highestLocal: Int? = null,
        currentEntity: LocationData<Entity>? = null,
        currentWorld: LocationData<World>? = null,
        currentPosition: LocationData<Vec3>? = null,
        currentRotation: LocationData<Vec2>? = null,
        addInstruction: ((Instruction) -> Unit)? = null,
    ): CompilationContext {
        return copy(highestLocal = highestLocal ?: this.highestLocal, currentEntity = currentEntity ?: this.currentEntity, currentWorld = currentWorld ?: this.currentWorld, currentPosition = currentPosition ?: this.currentPosition, currentRotation = currentRotation ?: this.currentRotation, addInstruction = addInstruction ?: this.addInstruction)
    }

    fun withResultStorage(requireResult: Boolean = false, requireSuccess: Boolean = false, exec: CompilationContext.(ResultStorage) -> Unit) {
        if(requireResult == requireSuccess) throw UnsupportedOperationException("calling withResultStorage() with requireResult == requireSuccess is not supported")
        val loc = LocationData.ofLocal<Int>(highestLocal)
        val res = if(requireResult) ResultStorage.withResult(loc) else ResultStorage.withSuccess(loc)
        val instr = Instructions2.createResultStorage(loc)
        addInstruction(instr)
        with(highestLocal = highestLocal + 1, addInstruction = instr.children::add).exec(res)
    }

    fun getScoreData(objective: LocationData<Objective>, name: LocationData<String>, autoCreate: Boolean, exec: CompilationContext.(scoreData: LocationData<Score>) -> Unit) {
        val scoreData = LocationData.ofLocal<Score>(highestLocal)
        val instruction = Instructions2.scoreData(objective, name, scoreData, autoCreate)
        addInstruction(instruction)
        if(autoCreate) {
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scoreData)
        }else {
            val nonNull = Instructions2.ifNotNull(scoreData as LocationData<Any>)
            instruction.with(nonNull)
            exec(with(highestLocal = highestLocal + 1, addInstruction = nonNull.children::add), scoreData)
        }
    }

    fun forPosition(pos: WorldCoordinates, exec: CompilationContext.(positionId: LocationData<Vec3>) -> Unit) {
        getPosition { basePositionId ->
            val positionId = LocationData.ofLocal<Vec3>(highestLocal)
            val instruction = Instructions.applyPositionWorld(basePositionId, positionId, pos)
            addInstruction(instruction)
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), positionId)
        }
    }

    fun getWorld(exec: CompilationContext.(worldId: LocationData<World>) -> Unit) {
        if(currentWorld != null) {
            exec(currentWorld)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData.ofLocal<World>(highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity as LocationData<Any>)
                val instruction = Instructions2.getEntityWorld(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(with(currentWorld = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getPosition(exec: CompilationContext.(positionId: LocationData<Vec3>) -> Unit) {
        if(currentPosition != null) {
            exec(currentPosition)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData.ofLocal<Vec3>(highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity as LocationData<Any>)
                val instruction = Instructions2.getEntityPosition(entity, local)
                nullCheck.with(instruction)
                addInstruction(nullCheck)
                exec(with(currentPosition = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getRotation(exec: CompilationContext.(rotationId: LocationData<Vec2>) -> Unit) {
        if(currentRotation != null) {
            exec(currentRotation)
        }else {
            forCurrentEntity { entity, _, _ ->
                val local = LocationData.ofLocal<Vec2>(highestLocal)
                val nullCheck = Instructions2.ifNotNull(entity as LocationData<Any>)
                val instruction = Instructions2.getEntityRotation(entity, local)
                instruction.with(nullCheck)
                addInstruction(nullCheck)
                exec(with(currentRotation = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun forScope(exec: CompilationContext.(scope: ScopeData) -> Unit) {
        val scope = ScopeData.ofId(highestLocal)
        val instruction = Instructions2.scope(scope)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), scope)
    }

    fun getObjective(name: String, exec: CompilationContext.(local: LocationData<Objective>) -> Unit) {
        val local = LocationData.ofLocal<Objective>(highestLocal)
        val instruction = Instructions2.ifNotNull(local as LocationData<Any>)
        addInstruction(Instructions2.getObjective(local, name).with(instruction))
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    fun forScoreHolder(holder: ScoreHolderArgument, exec: CompilationContext.(local: LocationData<String>) -> Unit) {
        if(holder.all) {
            forEachScoreHolder(single = holder.single, exec)
        }else if(holder.name != null) {
            exec(LocationData.ofString(holder.name))
        }else if(holder.selector != null) {
            val selector = if(holder.single) holder.selector.limit(1) else holder.selector
            forSelector(selector) { entity, _, _ ->
                val local = LocationData.ofLocal<String>(highestLocal)
                val instruction = Instructions2.getScoreboardName(entity, local)
                addInstruction(Instructions2.ifNotNull(entity as LocationData<Any>).with(instruction))
                exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
            }
        }
    }

    fun getScoreboardName(entity: LocationData<Entity>, exec: CompilationContext.(local: LocationData<String>) -> Unit) {
        val local = LocationData.ofLocal<String>(highestLocal)
        val instruction = Instructions2.getScoreboardName(entity, local)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    fun forCurrentEntity(exec: CompilationContext.(local: LocationData<Entity>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { scope ->
            if(currentEntity != null) {
                exec(this, currentEntity, scope, scope)
            }else {
                val local = LocationData.ofLocal<Entity>(highestLocal)
                val instruction = Instructions2.getCurrentEntity(local)
                addInstruction(instruction)
                exec(with(currentEntity = local, highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, scope, scope)
            }
        }
    }

    fun forEachEntity(exec: CompilationContext.(local: LocationData<Entity>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData.ofLocal<Entity>(highestLocal)
            val instruction = Instructions2.forEachEntity(local)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    fun forEachPlayer(exec: CompilationContext.(local: LocationData<Player>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData.ofLocal<Player>(highestLocal)
            val instruction = Instructions2.forEachPlayer(local)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    fun forEntity(entityUuid: UUID, exec: CompilationContext.(local: LocationData<Entity>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { breakScope ->
            val local = LocationData.ofLocal<Entity>(highestLocal)
            val instruction = Instructions2.forEntity(local, entityUuid)
            addInstruction(instruction)
            with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add).forScope { continueScope ->
                exec(local, breakScope, continueScope)
            }
        }
    }

    fun forPlayer(playerName: String, exec: CompilationContext.(local: LocationData<Player>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        forScope { scope ->
            val local = LocationData.ofLocal<Player>(highestLocal)
            val instruction = Instructions2.forPlayer(local, playerName)
            addInstruction(instruction)
            exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local, scope, scope)
        }
    }

    fun forEachScoreHolder(single: Boolean = false, exec: CompilationContext.(local: LocationData<String>) -> Unit) {
        val local = LocationData.ofLocal<String>(highestLocal)
        val instruction = Instructions2.forScoreHolders(local, single)
        addInstruction(instruction)
        exec(with(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
    }

    fun forSelector(selector: SelectorArgument, exec: CompilationContext.(local: LocationData<Entity>, breakScope: ScopeData, continueScope: ScopeData) -> Unit) {
        var limiterScope: ScopeData? = null
        var limiterCounter: LocationData<Int>? = null
        fun checkEntityCondition(context: CompilationContext, local: LocationData<Entity>, breakScope: ScopeData, continueScope: ScopeData) {
            var ctx = context
            val nonNull = Instructions2.ifNotNull(local as LocationData<Any>)
            val stacker = InstructionStacker(nonNull)
            if(selector.entityTypes != null) {
                stacker.stackInstruction(Instructions.checkEntityTypes(local, selector.entityTypes.data.toMutableList(), selector.entityTypes.negated))
            }
            if(selector.gamemode != null) {
                stacker.stackInstruction(Instructions2.checkGamemode(local, selector.gamemode.data, selector.gamemode.negated))
            }
            for(tag in selector.tags) {
                stacker.stackInstruction(Instructions2.checkEntityTag(local, tag.data, tag.negated))
            }
            if(selector.hasTags != null) {
                stacker.stackInstruction(Instructions2.checkEntityTags(local, selector.hasTags))
            }
            if(selector.yaw != null || selector.pitch != null) {
                val rotationLocal = LocationData.ofLocal<Vec2>(context.highestLocal)
                stacker.stackInstruction(Instructions2.getEntityRotation(local, rotationLocal))
                if(selector.yaw != null) stacker.stackInstruction(Instructions.checkRotationYaw(rotationLocal, selector.yaw))
                if(selector.pitch != null) stacker.stackInstruction(Instructions.checkRotationPitch(rotationLocal, selector.pitch))
                ctx = ctx.with(highestLocal = context.highestLocal + 1)
            }
            if(selector.team != null) {
                stacker.stackInstruction(Instructions2.checkTeam(local, selector.team.data, selector.team.negated))
            }
            if(selector.hasTeam != null) {
                stacker.stackInstruction(Instructions2.hasTeam(local, selector.hasTeam))
            }
            if(selector.scores.isNotEmpty()) {
                for((obj, range) in selector.scores) {
                    ctx.with(addInstruction = stacker::stackInstruction).getObjective(obj) { objective ->
                        getScoreboardName(local) { name ->
                            getScoreData(objective, name, false) { score ->
                                val instr = Instructions.checkInScoreRange(score, range)
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
            limiterScope = ScopeData.ofId(highestLocal)
            limiterCounter = LocationData.ofLocal(highestLocal + 1)
            val instr = Instructions2.limiter(limiterScope, limiterCounter, selector.limit)
            val newContext = with(highestLocal = highestLocal + 2, addInstruction = instr.children::add)
            addInstruction(instr)
            newContext
        } else this

        if(selector.playerName != null) {
            realContext.forPlayer(selector.playerName) { local, breakScope, continueScope -> checkEntityCondition(this, local as LocationData<Entity>, breakScope, continueScope) }
        }else if(selector.entityUuid != null) {
            realContext.forEntity(selector.entityUuid) { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else if(selector.self) {
            realContext.forCurrentEntity { local, breakScope, continueScope -> checkEntityCondition(this, local, breakScope, continueScope) }
        }else if(selector.selectPlayers()) {
            realContext.forEachPlayer { local, breakScope, continueScope -> checkEntityCondition(this, local as LocationData<Entity>, breakScope, continueScope) }
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