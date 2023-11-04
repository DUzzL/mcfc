package dev.epicpix.minecraftfunctioncompiler.compiler

import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument
import dev.epicpix.minecraftfunctioncompiler.il.Instruction
import dev.epicpix.minecraftfunctioncompiler.il.Instructions
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import java.util.*

data class CompilationContext(val highestLocal: Int = 0, val currentEntity: LocationData? = null, val addInstruction: (Instruction) -> Unit) {

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
            forSelector(holder.selector) { entity ->
                val local = LocationData(local = highestLocal)
                val instruction = Instructions.getScoreboardName(entity, local)
                exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
                addInstruction(Instructions.ifNotNull(entity).with(instruction))
            }
        }
    }

    fun forCurrentEntity(exec: CompilationContext.(local: LocationData, loopId: Int) -> Unit) {
        if(currentEntity != null) {
            val loopId = highestLocal
            val instruction = Instructions.emptyCondition(loopId)
            exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), currentEntity, loopId)
            addInstruction(instruction)
        }else {
            val loopId = highestLocal
            val local = LocationData(local = highestLocal + 1)
            val instruction = Instructions.currentEntity(local)
            exec(copy(currentEntity = local, highestLocal = highestLocal + 2, addInstruction = instruction.children::add), local, loopId)
            addInstruction(Instructions.emptyCondition(loopId).with(instruction))
        }
    }

    fun forEachEntity(exec: CompilationContext.(local: LocationData, loopId: Int) -> Unit) {
        val loopId = highestLocal
        val local = LocationData(local = highestLocal + 1)
        val instruction = Instructions.forEachEntity(local, loopId)
        exec(copy(highestLocal = highestLocal + 2, addInstruction = instruction.children::add), local, loopId)
        addInstruction(instruction)
    }

    fun forEachPlayer(exec: CompilationContext.(local: LocationData, loopId: Int) -> Unit) {
        val loopId = highestLocal
        val local = LocationData(local = highestLocal + 1)
        val instruction = Instructions.forEachPlayer(local, loopId)
        exec(copy(highestLocal = highestLocal + 2, addInstruction = instruction.children::add), local, loopId)
        addInstruction(instruction)
    }

    fun forEntity(entityUuid: UUID, exec: CompilationContext.(local: LocationData, loopId: Int) -> Unit) {
        val loopId = highestLocal
        val local = LocationData(local = highestLocal + 1)
        val instruction = Instructions.forEntity(local, entityUuid, loopId)
        exec(copy(highestLocal = highestLocal + 2, addInstruction = instruction.children::add), local, loopId)
        addInstruction(instruction)
    }

    fun forPlayer(playerName: String, exec: CompilationContext.(local: LocationData, loopId: Int) -> Unit) {
        val loopId = highestLocal
        val local = LocationData(local = highestLocal + 1)
        val instruction = Instructions.forPlayer(local, playerName)
        exec(copy(highestLocal = highestLocal + 2, addInstruction = instruction.children::add), local, loopId)
        addInstruction(Instructions.emptyCondition(loopId).with(instruction))
    }

    fun forEachScoreHolder(single: Boolean = false, exec: CompilationContext.(local: LocationData) -> Unit) {
        val local = LocationData(local = highestLocal)
        val instruction = Instructions.forScoreHolders(local, single)
        exec(copy(highestLocal = highestLocal + 1, addInstruction = instruction.children::add), local)
        addInstruction(instruction)
    }

    fun forSelector(selector: SelectorArgument, exec: CompilationContext.(local: LocationData) -> Unit) {
        var limiterId: Int? = null
        fun checkEntityCondition(context: CompilationContext, local: LocationData, loopId: Int) {
            val nonNull = Instructions.ifNotNull(local)
            for(tag in selector.tags) {
                if(!tag.negated) nonNull.with(Instructions.checkEntityTag(local, tag.data, loopId))
                else nonNull.with(Instructions.checkNotEntityTag(local, tag.data, loopId))
            }
            if(selector.hasTags != null) {
                if(selector.hasTags) nonNull.with(Instructions.checkEntityTags(local, loopId))
                else nonNull.with(Instructions.checkNotEntityTags(local, loopId))
            }
            exec(context.copy(addInstruction = nonNull.children::add), local)
            if(selector.limit == 1) {
                nonNull.with(Instructions.breakLoop(loopId))
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
            realContext.forPlayer(selector.playerName) { local, loopId -> checkEntityCondition(this, local, loopId) }
        }else if(selector.entityUuid != null) {
            realContext.forEntity(selector.entityUuid) { local, loopId -> checkEntityCondition(this, local, loopId) }
        }else if(selector.self) {
            realContext.forCurrentEntity { local, loopId -> checkEntityCondition(this, local, loopId) }
        }else if(selector.entityType == "minecraft:player") {
            realContext.forEachPlayer { local, loopId -> checkEntityCondition(this, local, loopId) }
        }else {
            realContext.forEachEntity { local, loopId -> checkEntityCondition(this, local, loopId) }
        }
    }

}