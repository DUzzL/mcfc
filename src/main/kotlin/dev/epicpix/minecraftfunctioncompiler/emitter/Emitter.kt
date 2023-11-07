package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.ArrayList_get
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.ArrayList_size
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_getEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_getServer
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_withEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_withLevel
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_withPosition
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.CommandSourceStack_withRotation
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_addTag
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_getRotationVector
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_getScoreboardName
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_getTags
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_kill
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_level
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_position
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Entity_removeTag
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Iterable_iterator
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Map_keys
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Math_floorDiv
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Math_floorMod
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Math_max
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Math_min
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.MinecraftServer_getAllLevels
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.MinecraftServer_getPlayerList
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.PlayerList_getPlayerByName
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.PlayerList_getPlayers
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Score_getScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Score_setScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Scoreboard_getObjective
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Scoreboard_getOrCreatePlayerScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Scoreboard_playerScores
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Scoreboard_resetPlayerScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.ServerFunctionManager_execute
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.ServerLevel_getAllEntities
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.ServerLevel_getEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Set_contains
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedData.Companion.Set_size
import dev.epicpix.minecraftfunctioncompiler.il.*
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Score
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

private data class EmitControlFlow(val breakLabel: Label, val continueLabel: Label)

fun emitBytecode(instructions: List<Instruction>, fieldStorage: CodeFieldStorage, codeWriter: CodeWriter, definedFunctions: Map<ResourceLocation, List<Command>>) {
    for(instruction in instructions) {
        emitInstruction(instruction, fieldStorage, codeWriter, definedFunctions)
    }
}

private fun LocationData.load(codeWriter: CodeWriter, transforms: Map<Int, String>) {
    if(this.string != null) {
        codeWriter.writeString(string)
    }else if(this.local != null) {
        codeWriter.loadLocal(transforms[local]!!)
    }
}

private fun emitInstruction(
    instruction: Instruction,
    fieldStorage: CodeFieldStorage,
    codeWriter: CodeWriter,
    definedFunctions: Map<ResourceLocation, List<Command>>,
    transforms: Map<Int, String> = mapOf(),
    loops: Map<Int, EmitControlFlow> = mapOf()
) {
    val newTransforms = transforms.toMutableMap()
    val newLoops = loops.toMutableMap()

    fun run() = instruction.children.forEach { emitInstruction(it, fieldStorage, codeWriter, definedFunctions, newTransforms, newLoops) }

    when(instruction.type) {
        InstructionType.GET_CURRENT_ENTITY -> {
            val data = instruction.data as InstructionDataLocation
            val uniqueId = codeWriter.generateUniqueLocal("\$GetCurrentEntity")
            newTransforms[data.local.local!!] = uniqueId
            val end = codeWriter.defineLocal(uniqueId, Entity::class.java)

            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getEntity)
            codeWriter.storeLocal(uniqueId)

            run()

            end()
        }
        InstructionType.IF_NOT_NULL -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.executeIf(Opcodes.IFNONNULL) { run() }
        }
        InstructionType.IF_NULL -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.executeIf(Opcodes.IFNULL) { run() }
        }
        InstructionType.ADD_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationString
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(Entity_addTag)
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.REMOVE_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationString
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(Entity_removeTag)
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
        InstructionType.EMPTY_CONDITION -> {
            val data = instruction.data as InstructionDataLoop
            val label = Label()
            newLoops[data.loopId] = EmitControlFlow(label, label)

            run()

            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.BREAK_LOOP -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newLoops[data.loopId]!!.breakLabel)
        }
        InstructionType.CONTINUE_LOOP -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationStringLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(Set_contains)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_NOT_ENTITY_TAG -> {
            val data = instruction.data as InstructionDataLocationStringLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(Set_contains)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_ENTITY_TAGS -> {
            val data = instruction.data as InstructionDataLocationLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeMapped(Set_size)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.CHECK_NOT_ENTITY_TAGS -> {
            val data = instruction.data as InstructionDataLocationLoop
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getTags)
            codeWriter.writeMapped(Set_size)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, newLoops[data.loopId]!!.continueLabel)
        }
        InstructionType.LIMITER -> {
            val data = instruction.data as InstructionDataIntLoop
            val uniqueId = codeWriter.generateUniqueLocal("\$LimitRemaining")
            newTransforms[data.loopId] = uniqueId
            val end = codeWriter.defineLocal(uniqueId, Int::class.javaPrimitiveType!!)
            codeWriter.writeInt(data.value)
            codeWriter.storeLocal(uniqueId)

            val label = Label()
            newLoops[data.loopId] = EmitControlFlow(label, label)

            run()

            end()
            codeWriter.methodVisitor.visitLabel(label)
        }
        InstructionType.CHECK_LIMIT -> {
            val data = instruction.data as InstructionDataLoop
            codeWriter.methodVisitor.visitIincInsn(codeWriter.getLocalIndex(newTransforms[data.loopId]!!), -1)
            codeWriter.loadLocal(newTransforms[data.loopId]!!)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.loopId]!!.breakLabel)
        }
        InstructionType.FOR_EACH_PLAYER -> {
            val data = instruction.data as InstructionDataLocation

            val uidLocal = codeWriter.generateUniqueLocal("\$Players")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getPlayerList)
            codeWriter.writeMapped(PlayerList_getPlayers)
            val end = codeWriter.defineLocal(uidLocal, List::class.java)
            codeWriter.storeLocal(uidLocal)
            codeWriter.createListForEachLoop(uidLocal, Entity::class.java, true) { _, getter ->
                val uidLocal2 = codeWriter.generateUniqueLocal("\$Entity")
                val lend = codeWriter.defineLocal(uidLocal2, Entity::class.java)
                codeWriter.storeLocal(uidLocal2)
                newTransforms[data.local.local!!] = uidLocal2
                run()
                lend()
            }
            end()
        }
        InstructionType.FOR_EACH_ENTITY -> {
            val data = instruction.data as InstructionDataLocation

            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getAllLevels)
            val end = codeWriter.defineLocal(uidLocalLevels, Iterable::class.java)
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) { _, _ ->
                val uidLocalEntities = codeWriter.generateUniqueLocal("\$Entities")
                codeWriter.writeMapped(ServerLevel_getAllEntities)
                codeWriter.writeMapped(Iterable_iterator)
                val end2 = codeWriter.defineLocal(uidLocalEntities, Iterator::class.java)
                codeWriter.storeLocal(uidLocalEntities)
                codeWriter.createIteratorForEachLoop(uidLocalEntities, Entity::class.java, true) { _, _ ->
                    val uidLocalEntity = codeWriter.generateUniqueLocal("\$Entity")
                    val lend = codeWriter.defineLocal(uidLocalEntity, Entity::class.java)
                    codeWriter.storeLocal(uidLocalEntity)
                    newTransforms[data.local.local!!] = uidLocalEntity
                    run()
                    lend()
                }

                end2()
            }

            end()
        }
        InstructionType.FOR_PLAYER -> {
            val data = instruction.data as InstructionDataLocationString

            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(PlayerList_getPlayerByName)
            val uidLocal = codeWriter.generateUniqueLocal("\$Player")
            val end = codeWriter.defineLocal(uidLocal, ServerPlayer::class.java)
            newTransforms[data.local.local!!] = uidLocal
            codeWriter.storeLocal(uidLocal)

            run()

            end()
        }
        InstructionType.FOR_ENTITY -> {
            val data = instruction.data as InstructionDataLocationUUID

            val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
            codeWriter.loadLocal("CommandSourceStack")
            codeWriter.writeMapped(CommandSourceStack_getServer)
            codeWriter.writeMapped(MinecraftServer_getAllLevels)
            val end = codeWriter.defineLocal(uidLocalLevels, Iterable::class.java)
            codeWriter.storeLocal(uidLocalLevels)
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) { controlFlow, _ ->
                fieldStorage.loadUUID(codeWriter, data.value)
                codeWriter.writeMapped(ServerLevel_getEntity)
                val uidLocal = codeWriter.generateUniqueLocal("\$Entity")
                val end2 = codeWriter.defineLocal(uidLocal, Entity::class.java)
                newTransforms[data.local.local!!] = uidLocal
                codeWriter.storeLocal(uidLocal)
                codeWriter.loadLocal(uidLocal)
                codeWriter.executeIf(Opcodes.IFNONNULL) {
                    run()
                }
                end2()
            }

            end()
        }
        InstructionType.FOR_SCORE_HOLDERS -> {
            val data = instruction.data as InstructionDataLocationBoolean

            codeWriter.newObject(ArrayList::class.java, Collection::class.java) {
                fieldStorage.loadScoreboard(codeWriter)
                codeWriter.writeMapped(Scoreboard_playerScores)
                codeWriter.writeMapped(Map_keys)
            }

            val uidLocal = codeWriter.generateUniqueLocal("\$Array")
            val end = codeWriter.defineLocal(uidLocal, ArrayList::class.java)
            codeWriter.storeLocal(uidLocal)
            if(!data.value) {
                codeWriter.createListForEachLoop(uidLocal, String::class.java) { _, getter ->
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, String::class.java)
                    getter()
                    codeWriter.storeLocal(uidLocalName)
                    newTransforms[data.local.local!!] = uidLocalName
                    run()
                    end2()
                }
            }else {
                codeWriter.loadLocal(uidLocal)
                codeWriter.writeMapped(ArrayList_size)
                codeWriter.executeIf(Opcodes.IFNE) {
                    val uidLocalName = codeWriter.generateUniqueLocal("\$Name")
                    val end2 = codeWriter.defineLocal(uidLocalName, String::class.java)
                    codeWriter.loadLocal(uidLocal)
                    codeWriter.writeInt(0)
                    codeWriter.writeMapped(ArrayList_get)
                    codeWriter.castType(String::class.java)
                    codeWriter.storeLocal(uidLocalName)
                    newTransforms[data.local.local!!] = uidLocalName
                    run()
                    end2()
                }

            }

            end()

        }
        InstructionType.GET_SCOREBOARD_NAME -> {
            val data = instruction.data as InstructionDataLocationLocation

            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getScoreboardName)

            val uidLocal = codeWriter.generateUniqueLocal("\$ScoreboardName")
            val end = codeWriter.defineLocal(uidLocal, String::class.java)
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local2.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.SET_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocationInt

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
            codeWriter.writeInt(data.value)
            codeWriter.writeMapped(Score_setScore)
        }
        InstructionType.ADD_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocationInt

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
            codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
            codeWriter.writeMapped(Score_getScore)
            if(data.value >= 0) {
                codeWriter.writeInt(data.value)
                codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
            }else {
                codeWriter.writeInt(-data.value)
                codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
            }
            codeWriter.writeMapped(Score_setScore)
        }
        InstructionType.RUN_SCORE_OPERATION -> {
            val data = instruction.data as InstructionDataRunScoreOperation

            when(data.operation) {
                ScoreboardOperation.SET -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                        codeWriter.writeMapped(Score_getScore)
                        codeWriter.writeMapped(Score_setScore)
                    }
                }
                ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                    }else {
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    }
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                    codeWriter.writeMapped(Score_getScore)
                    if(data.operation == ScoreboardOperation.PLUS) codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    else if(data.operation == ScoreboardOperation.MINUS) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    } else codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                    codeWriter.writeMapped(Score_setScore)
                }
                ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    codeWriter.writeMapped(Score_getScore)
                    val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                    val endBeh = codeWriter.defineLocal(uniqBeh, Int::class.javaPrimitiveType!!)
                    codeWriter.storeLocal(uniqBeh)

                    fieldStorage.loadScoreboard(codeWriter)
                    data.sourceName.load(codeWriter, newTransforms)
                    data.sourceObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    val uniq = codeWriter.generateUniqueLocal("\$Score")
                    val end = codeWriter.defineLocal(uniq, Score::class.java)
                    codeWriter.storeLocal(uniq)
                    codeWriter.loadLocal(uniq)
                    codeWriter.writeMapped(Score_getScore)
                    codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newLoops[data.exceptionLoopId]!!.breakLabel)
                    codeWriter.loadLocal(uniq)
                    codeWriter.loadLocal(uniqBeh)
                    codeWriter.loadLocal(uniq)
                    codeWriter.writeMapped(Score_getScore)
                    if(data.operation == ScoreboardOperation.DIVIDE) codeWriter.writeMapped(Math_floorDiv)
                    else codeWriter.writeMapped(Math_floorMod)
                    codeWriter.writeMapped(Score_setScore)
                    end()
                    endBeh()
                }
                ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                        codeWriter.writeMapped(Score_getScore)
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.writeMapped(Score_getScore)
                        if(data.operation == ScoreboardOperation.MIN) codeWriter.writeMapped(Math_min)
                        else codeWriter.writeMapped(Math_max)
                        codeWriter.writeMapped(Score_setScore)
                    }
                }
                ScoreboardOperation.SWAP -> {
                    fieldStorage.loadScoreboard(codeWriter)
                    data.targetName.load(codeWriter, newTransforms)
                    data.targetObjective.load(codeWriter, newTransforms)
                    codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                    if(data.sourceObjective == data.targetObjective && data.sourceName == data.targetName) {
                        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                    }else {
                        val targetId = codeWriter.generateUniqueLocal("\$Score")
                        val targetEnd = codeWriter.defineLocal(targetId, Score::class.java)
                        codeWriter.storeLocal(targetId)

                        fieldStorage.loadScoreboard(codeWriter)
                        data.sourceName.load(codeWriter, newTransforms)
                        data.sourceObjective.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
                        val sourceId = codeWriter.generateUniqueLocal("\$Score")
                        val sourceEnd = codeWriter.defineLocal(sourceId, Score::class.java)
                        codeWriter.storeLocal(sourceId)

                        codeWriter.loadLocal(sourceId)
                        codeWriter.loadLocal(targetId)
                        codeWriter.writeMapped(Score_getScore)
                        codeWriter.loadLocal(targetId)
                        codeWriter.loadLocal(sourceId)
                        codeWriter.writeMapped(Score_getScore)
                        codeWriter.writeMapped(Score_setScore)
                        codeWriter.writeMapped(Score_setScore)

                        sourceEnd()
                        targetEnd()
                    }
                }
            }
        }
        InstructionType.GET_OBJECTIVE -> {
            val data = instruction.data as InstructionDataLocationString

            fieldStorage.loadScoreboard(codeWriter)
            codeWriter.writeString(data.value)
            codeWriter.writeMapped(Scoreboard_getObjective)

            val uidLocal = codeWriter.generateUniqueLocal("\$Objective")
            val end = codeWriter.defineLocal(uidLocal, Objective::class.java)
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.CALL_FUNCTIONS -> {
            val data = instruction.data as InstructionDataFunctionCall

            for(func in data.functions) {
                if(definedFunctions.containsKey(func)) {
                    codeWriter.loadLocal("CommandSourceStack")
                    if(data.entity != null) {
                        data.entity.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withEntity)
                    }
                    if(data.world != null) {
                        data.world.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withLevel)
                    }
                    if(data.position != null) {
                        data.position.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withPosition)
                    }
                    if(data.rotation != null) {
                        data.rotation.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withRotation)
                    }
                    codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}", "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }else {
                    fieldStorage.loadServerFunctionManager(codeWriter)
                    fieldStorage.loadFunction(codeWriter, func)
                    codeWriter.loadLocal("CommandSourceStack")
                    if(data.entity != null) {
                        data.entity.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withEntity)
                    }
                    if(data.world != null) {
                        data.world.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withLevel)
                    }
                    if(data.position != null) {
                        data.position.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withPosition)
                    }
                    if(data.rotation != null) {
                        data.rotation.load(codeWriter, newTransforms)
                        codeWriter.writeMapped(CommandSourceStack_withRotation)
                    }
                    codeWriter.writeNull()
                    codeWriter.writeNull()
                    codeWriter.writeMapped(ServerFunctionManager_execute)
                    codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                }
            }
        }
        InstructionType.KILL_ENTITY -> {
            val data = instruction.data as InstructionDataLocation
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_kill)
        }
        InstructionType.RESET_SCORE -> {
            val data = instruction.data as InstructionDataLocationLocation

            fieldStorage.loadScoreboard(codeWriter)
            data.local2.load(codeWriter, newTransforms)
            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_resetPlayerScore)
        }
        InstructionType.CHECK_SCORE_RANGE -> {
            val data = instruction.data as InstructionDataCheckScoreRange
            fieldStorage.loadScoreboard(codeWriter)
            data.name.load(codeWriter, newTransforms)
            data.objective.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
            codeWriter.writeMapped(Score_getScore)
            if(data.range.minimum != null && data.range.maximum == null) {
                codeWriter.writeInt(data.range.minimum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPGE else Opcodes.IF_ICMPLT) {
                    run()
                }
            }else if(data.range.minimum == null && data.range.maximum != null) {
                codeWriter.writeInt(data.range.maximum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPLE else Opcodes.IF_ICMPGT) {
                    run()
                }
            }else if(data.range.minimum != null && data.range.minimum == data.range.maximum) {
                codeWriter.writeInt(data.range.minimum)
                codeWriter.executeIf(if(!data.negate) Opcodes.IF_ICMPEQ else Opcodes.IF_ICMPNE) {
                    run()
                }
            }else if(data.range.minimum != null && data.range.maximum != null) {
                val uid = codeWriter.generateUniqueLocal("\$Score")
                val end = codeWriter.defineLocal(uid, Int::class.javaPrimitiveType!!)
                codeWriter.storeLocal(uid)
                if(!data.negate) {
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(data.range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(data.range.maximum)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            run()
                        }
                    }
                }else {
                    val skip = Label()
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(data.range.minimum)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(data.range.maximum)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, skip)
                        }
                    }
                    run()
                    codeWriter.methodVisitor.visitLabel(skip)
                }
                end()
            }
        }
        InstructionType.CHECK_SCORE_COMPARE -> {
            val data = instruction.data as InstructionDataCheckScoreCompare
            fieldStorage.loadScoreboard(codeWriter)
            data.sourceName.load(codeWriter, newTransforms)
            data.sourceObjective.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
            codeWriter.writeMapped(Score_getScore)

            fieldStorage.loadScoreboard(codeWriter)
            data.targetName.load(codeWriter, newTransforms)
            data.targetObjective.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Scoreboard_getOrCreatePlayerScore)
            codeWriter.writeMapped(Score_getScore)

            when(data.compareOperation) {
                ScoreboardCompareOperation.LT -> { codeWriter.executeIf(Opcodes.IF_ICMPLT) { run() } }
                ScoreboardCompareOperation.LE -> { codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() } }
                ScoreboardCompareOperation.EQ -> { codeWriter.executeIf(Opcodes.IF_ICMPEQ) { run() } }
                ScoreboardCompareOperation.NE -> { codeWriter.executeIf(Opcodes.IF_ICMPNE) { run() } }
                ScoreboardCompareOperation.GE -> { codeWriter.executeIf(Opcodes.IF_ICMPGE) { run() } }
                ScoreboardCompareOperation.GT -> { codeWriter.executeIf(Opcodes.IF_ICMPGT) { run() } }
            }

        }
        InstructionType.GET_ENTITY_WORLD -> {
            val data = instruction.data as InstructionDataLocationLocation

            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_level)

            val uidLocal = codeWriter.generateUniqueLocal("\$World")
            val end = codeWriter.defineLocal(uidLocal, ServerLevel::class.java)
            codeWriter.castType(ServerLevel::class.java)
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local2.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.GET_ENTITY_POSITION -> {
            val data = instruction.data as InstructionDataLocationLocation

            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_position)

            val uidLocal = codeWriter.generateUniqueLocal("\$Position")
            val end = codeWriter.defineLocal(uidLocal, Vec3::class.java)
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local2.local!!] = uidLocal
            run()
            end()
        }
        InstructionType.GET_ENTITY_ROTATION -> {
            val data = instruction.data as InstructionDataLocationLocation

            data.local.load(codeWriter, newTransforms)
            codeWriter.writeMapped(Entity_getRotationVector)

            val uidLocal = codeWriter.generateUniqueLocal("\$Rotation")
            val end = codeWriter.defineLocal(uidLocal, Vec2::class.java)
            codeWriter.storeLocal(uidLocal)
            newTransforms[data.local2.local!!] = uidLocal
            run()
            end()
        }
    }
}