package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.*
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.ArrayList_get
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.ArrayList_size
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Iterable_iterator
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Map_keySet
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Math_floor
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Math_floorDiv
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Math_floorMod
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Math_max
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Math_min
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Set_contains
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.Set_size
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitions.String_equals
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.AttributeInstance_setBaseValue
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.AttributeMap_getInstance
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_getEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_getServer
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_withEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_withLevel
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_withPosition
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.CommandSourceStack_withRotation
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_addTag
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_getRotationVector
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_getScoreboardName
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_getTags
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_getTeam
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_kill
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_level
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_position
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Entity_removeTag
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.GameType_ADVENTURE
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.GameType_CREATIVE
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.GameType_SPECTATOR
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.GameType_SURVIVAL
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.LivingEntity_getAttributes
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.MinecraftServer_getAllLevels
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.MinecraftServer_getPlayerList
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.PlayerList_getPlayerByName
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.PlayerList_getPlayers
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Score_getScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Score_setScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Scoreboard_getObjective
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Scoreboard_getOrCreatePlayerScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Scoreboard_getScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Scoreboard_playerScores
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Scoreboard_resetPlayerScore
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerFunctionManager_execute
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerLevel_getAllEntities
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerLevel_getEntity
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerPlayerGameMode_getGameModeForPlayer
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerPlayer_gameMode
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.ServerPlayer_setGameMode
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Team_getName
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Vec3_x
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Vec3_y
import dev.epicpix.minecraftfunctioncompiler.emitter.MappedUsageDefinitions.Vec3_z
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardCompareOperation
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardOperation
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Score
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun registerInstructionEmitters() {
    registerEmitterInstruction(InstructionTypes.GET_CURRENT_ENTITY) {
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(CommandSourceStack_getEntity)
        runStored("entity", "\$GetCurrentEntity", Entity::class.java)
    }
    registerEmitterInstruction(InstructionTypes.ADD_ENTITY_TAG) {
        load("entity")
        codeWriter.writeString(instruction["tag"])
        codeWriter.writeMapped(Entity_addTag)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.REMOVE_ENTITY_TAG) {
        load("entity")
        codeWriter.writeString(instruction["tag"])
        codeWriter.writeMapped(Entity_removeTag)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TAG) {
        load("entity")
        codeWriter.writeMapped(Entity_getTags)
        codeWriter.writeString(instruction["tag"])
        codeWriter.writeMapped(Set_contains)

        val label = Label()
        codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TAGS) {
        load("entity")
        codeWriter.writeMapped(Entity_getTags)
        codeWriter.writeMapped(Set_size)

        val label = Label()
        codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }
    registerEmitterInstruction(InstructionTypes.FOR_EACH_PLAYER) {
        val uidLocal = codeWriter.generateUniqueLocal("\$Players")
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(CommandSourceStack_getServer)
        codeWriter.writeMapped(MinecraftServer_getPlayerList)
        codeWriter.writeMapped(PlayerList_getPlayers)
        val end = codeWriter.storeLocal(uidLocal, List::class.java)
        codeWriter.createListForEachLoop(uidLocal, Entity::class.java) {
            runStored("entity", "\$Entity", Entity::class.java)
        }
        end()
    }
    registerEmitterInstruction(InstructionTypes.FOR_EACH_ENTITY) {
        val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(CommandSourceStack_getServer)
        codeWriter.writeMapped(MinecraftServer_getAllLevels)
        val end = codeWriter.storeLocal(uidLocalLevels, Iterable::class.java)
        codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) {
            val uidLocalEntities = codeWriter.generateUniqueLocal("\$Entities")
            codeWriter.writeMapped(ServerLevel_getAllEntities)
            codeWriter.writeMapped(Iterable_iterator)
            val end2 = codeWriter.storeLocal(uidLocalEntities, Iterator::class.java)
            codeWriter.createIteratorForEachLoop(uidLocalEntities, Entity::class.java, true) {
                runStored("entity", "\$Entity", Entity::class.java)
            }

            end2()
        }

        end()
    }
    registerEmitterInstruction(InstructionTypes.FOR_PLAYER) {
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(CommandSourceStack_getServer)
        codeWriter.writeString(instruction["name"])
        codeWriter.writeMapped(PlayerList_getPlayerByName)
        runStored("entity", "\$Player", ServerPlayer::class.java)
    }
    registerEmitterInstruction(InstructionTypes.FOR_ENTITY) {
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(CommandSourceStack_getServer)
        codeWriter.writeMapped(MinecraftServer_getAllLevels)
        val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
        val end = codeWriter.storeLocal(uidLocalLevels, Iterable::class.java)
        codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) {
            fieldStorage.loadUUID(codeWriter, instruction["uuid"])
            codeWriter.writeMapped(ServerLevel_getEntity)
            val uidLocal = codeWriter.generateUniqueLocal("\$Entity")
            val end2 = storeCreate("entity", uidLocal, Entity::class.java)
            codeWriter.loadLocal(uidLocal)
            val endIf = codeWriter.executeIf(Opcodes.IFNONNULL)
            run()
            endIf()
            end2()
        }

        end()
    }
    registerEmitterInstruction(InstructionTypes.FOR_SCORE_HOLDERS) {
        val endNew = codeWriter.newObject(ArrayList::class.java, Collection::class.java)
        fieldStorage.loadScoreboard(codeWriter)
        codeWriter.writeMapped(Scoreboard_playerScores, true)
        codeWriter.writeMapped(Map_keySet)
        endNew()

        val uidLocal = codeWriter.generateUniqueLocal("\$Array")
        val end = codeWriter.storeLocal(uidLocal, ArrayList::class.java)
        if(!instruction.get<Boolean>("single")) {
            codeWriter.createListForEachLoop(uidLocal, String::class.java) {
                runStored("local", "\$Name", String::class.java)
            }
        }else {
            codeWriter.loadLocal(uidLocal)
            codeWriter.writeMapped(ArrayList_size)
            val endIf = codeWriter.executeIf(Opcodes.IFNE)
            codeWriter.loadLocal(uidLocal)
            codeWriter.writeInt(0)
            codeWriter.writeMapped(ArrayList_get)
            codeWriter.castType(String::class.java)
            runStored("local", "\$Name", String::class.java)
            endIf()
        }

        end()

    }
    registerEmitterInstruction(InstructionTypes.GET_SCOREBOARD_NAME) {
        load("entity")
        codeWriter.writeMapped(Entity_getScoreboardName)
        runStored("name", "\$ScoreboardName", String::class.java)
    }
    registerEmitterInstruction(InstructionTypes.SCORE_DATA) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        load("objective")
        codeWriter.writeMapped(if(instruction["autoCreate"]) Scoreboard_getOrCreatePlayerScore else Scoreboard_getScore)
        runStored("score", "\$ScoreData", Score::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_SCORE_VALUE) {
        load("score")
        codeWriter.writeMapped(Score_getScore)
        runStored("scoreValue", "\$ScoreValue", Int::class.javaPrimitiveType!!)
    }
    registerEmitterInstruction(InstructionTypes.SET_SCORE) {
        load("score")
        load("value")
        codeWriter.writeMapped(Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.ADD_SCORE) {
        load("score")
        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
        codeWriter.writeMapped(Score_getScore)
        load("value")
        codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
        codeWriter.writeMapped(Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.REMOVE_SCORE) {
        load("score")
        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
        codeWriter.writeMapped(Score_getScore)
        load("value")
        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
        codeWriter.writeMapped(Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.RUN_SCORE_OPERATION) {
        when(val operation = instruction.get<ScoreboardOperation>("operation")) {
            ScoreboardOperation.SET -> {
                load("target")
                load("source")
                codeWriter.writeMapped(Score_getScore)
                codeWriter.writeMapped(Score_setScore)
            }
            ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.writeMapped(Score_getScore)
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.writeMapped(Score_getScore)
                when(operation) {
                    ScoreboardOperation.PLUS -> codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    ScoreboardOperation.MINUS -> {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    }
                    else -> codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                }
                codeWriter.writeMapped(Score_setScore)
            }
            ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                load("target")
                codeWriter.writeMapped(Score_getScore)
                val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                val endBeh = codeWriter.storeLocal(uniqBeh, Int::class.javaPrimitiveType!!)

                load("source")
                val uniq = codeWriter.generateUniqueLocal("\$Score")
                val end = codeWriter.storeLocal(uniq, Score::class.java)
                codeWriter.loadLocal(uniq)
                codeWriter.writeMapped(Score_getScore)
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scopeId("exceptionScope")]!!.breakLabel)
                codeWriter.loadLocal(uniq)
                codeWriter.loadLocal(uniqBeh)
                codeWriter.loadLocal(uniq)
                codeWriter.writeMapped(Score_getScore)
                if(operation == ScoreboardOperation.DIVIDE) codeWriter.writeMapped(Math_floorDiv)
                else codeWriter.writeMapped(Math_floorMod)
                codeWriter.writeMapped(Score_setScore)
                end()
                endBeh()
            }
            ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.writeMapped(Score_getScore)
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.writeMapped(Score_getScore)
                if(operation == ScoreboardOperation.MIN) codeWriter.writeMapped(Math_min)
                else codeWriter.writeMapped(Math_max)
                codeWriter.writeMapped(Score_setScore)
            }
            ScoreboardOperation.SWAP -> {
                load("target")
                val targetId = codeWriter.generateUniqueLocal("\$Score")
                val targetEnd = codeWriter.storeLocal(targetId, Score::class.java)

                load("source")
                val sourceId = codeWriter.generateUniqueLocal("\$Score")
                val sourceEnd = codeWriter.storeLocal(sourceId, Score::class.java)

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
    registerEmitterInstruction(InstructionTypes.GET_OBJECTIVE) {
        fieldStorage.loadScoreboard(codeWriter)
        codeWriter.writeString(instruction["name"])
        codeWriter.writeMapped(Scoreboard_getObjective)
        runStored("objective", "\$Objective", Objective::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CALL_FUNCTIONS) {
        for(func in instruction.get<List<DataLocation>>("functions")) {
            if(!compiledFunctions.contains(func)) {
                fieldStorage.loadServerFunctionManager(codeWriter)
                fieldStorage.loadFunction(codeWriter, func)
            }
            codeWriter.loadLocal("CommandSourceStack")
            if(instruction.get<LocationData?>("entity") != null) {
                load("entity")
                codeWriter.writeMapped(CommandSourceStack_withEntity)
            }
            if(instruction.get<LocationData?>("world") != null) {
                load("world")
                codeWriter.writeMapped(CommandSourceStack_withLevel)
            }
            if(instruction.get<LocationData?>("position") != null) {
                load("position")
                codeWriter.writeMapped(CommandSourceStack_withPosition)
            }
            if(instruction.get<LocationData?>("rotation") != null) {
                load("rotation")
                codeWriter.writeMapped(CommandSourceStack_withRotation)
            }
            if(compiledFunctions.contains(func)) {
                codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}", "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
            }else {
                codeWriter.writeNull()
                codeWriter.writeNull()
                codeWriter.writeMapped(ServerFunctionManager_execute)
            }
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
    }
    registerEmitterInstruction(InstructionTypes.KILL_ENTITY) {
        load("entity")
        codeWriter.writeMapped(Entity_kill)
    }
    registerEmitterInstruction(InstructionTypes.RESET_SCORE) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        load("objective")
        codeWriter.writeMapped(Scoreboard_resetPlayerScore)
    }
    registerEmitterInstruction(InstructionTypes.RESET_PLAYER_SCORE) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        codeWriter.writeNull()
        codeWriter.writeMapped(Scoreboard_resetPlayerScore)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_RANGE) {
        load("score")
        codeWriter.writeMapped(Score_getScore)
        val range = instruction.get<IntRangeArgument>("range")
        val negate = instruction.get<Boolean>("negate")
        if(range.minimum != null && range.maximum == null) {
            codeWriter.writeInt(range.minimum!!)
            val endIf = codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPGE else Opcodes.IF_ICMPLT)
            run()
            endIf()
        }else if(range.minimum == null && range.maximum != null) {
            codeWriter.writeInt(range.maximum!!)
            val endIf = codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPLE else Opcodes.IF_ICMPGT)
            run()
            endIf()
        }else if(range.minimum != null && range.minimum == range.maximum) {
            codeWriter.writeInt(range.minimum!!)
            val endIf = codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPEQ else Opcodes.IF_ICMPNE)
            run()
            endIf()
        }else if(range.minimum != null) {
            val uid = codeWriter.generateUniqueLocal("\$Score")
            val end = codeWriter.storeLocal(uid, Int::class.javaPrimitiveType!!)
            if(!negate) {
                codeWriter.loadLocal(uid)
                codeWriter.writeInt(range.minimum!!)
                val endIf = codeWriter.executeIf(Opcodes.IF_ICMPGE)
                codeWriter.loadLocal(uid)
                codeWriter.writeInt(range.maximum!!)
                val endIf2 = codeWriter.executeIf(Opcodes.IF_ICMPLE)
                run()
                endIf2()
                endIf()
            }else {
                val skip = Label()
                codeWriter.loadLocal(uid)
                codeWriter.writeInt(range.minimum!!)
                val endIf = codeWriter.executeIf(Opcodes.IF_ICMPGE)
                codeWriter.loadLocal(uid)
                codeWriter.writeInt(range.maximum!!)
                val endIf2 = codeWriter.executeIf(Opcodes.IF_ICMPLE)
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, skip)
                endIf2()
                endIf()
                run()
                codeWriter.methodVisitor.visitLabel(skip)
            }
            end()
        }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_COMPARE) {
        load("source")
        codeWriter.writeMapped(Score_getScore)

        load("target")
        codeWriter.writeMapped(Score_getScore)

        val endIf = codeWriter.executeIf(when(instruction.get<ScoreboardCompareOperation>("compareOperation")) {
            ScoreboardCompareOperation.LT -> Opcodes.IF_ICMPLT
            ScoreboardCompareOperation.LE -> Opcodes.IF_ICMPLE
            ScoreboardCompareOperation.EQ -> Opcodes.IF_ICMPEQ
            ScoreboardCompareOperation.NE -> Opcodes.IF_ICMPNE
            ScoreboardCompareOperation.GE -> Opcodes.IF_ICMPGE
            ScoreboardCompareOperation.GT -> Opcodes.IF_ICMPGT
        })
        run()
        endIf()
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_WORLD) {
        load("entity")
        codeWriter.writeMapped(Entity_level)
        codeWriter.castType(ServerLevel::class.java)
        runStored("world", "\$World", ServerLevel::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_POSITION) {
        load("entity")
        codeWriter.writeMapped(Entity_position)
        runStored("position", "\$Position", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_ROTATION) {
        load("entity")
        codeWriter.writeMapped(Entity_getRotationVector)
        runStored("rotation", "\$Rotation", Vec2::class.java)
    }
    registerEmitterInstruction(InstructionTypes.APPLY_POSITION_WORLD) {
        val endNew = codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java)
        val pos = instruction.get<WorldCoordinates>("position")
        if(pos.isXRelative) {
            load("from")
            codeWriter.writeMapped(Vec3_x, true)
            if(pos.x != 0.0) {
                codeWriter.writeDouble(pos.x)
                codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
            }
        }else {
            codeWriter.writeDouble(pos.x)
        }
        if(pos.isYRelative) {
            load("from")
            codeWriter.writeMapped(Vec3_y, true)
            if(pos.y != 0.0) {
                codeWriter.writeDouble(pos.y)
                codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
            }
        }else {
            codeWriter.writeDouble(pos.y)
        }
        if(pos.isZRelative) {
            load("from")
            codeWriter.writeMapped(Vec3_z, true)
            if(pos.z != 0.0) {
                codeWriter.writeDouble(pos.z)
                codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
            }
        }else {
            codeWriter.writeDouble(pos.z)
        }
        endNew()
        runStored("to", "\$Position", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TYPES) {
        val label = Label()
        for(res in instruction.get<List<DataLocation>>("types")) {
            load("entity")
            codeWriter.methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, mapEntityTypeClass(res)?.descriptorString()?.run { substring(1, length - 1) })
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, label)
        }
        if(!instruction.get<Boolean>("negated")) {
            val label2 = Label()
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label2)
            codeWriter.methodVisitor.visitLabel(label)
            run()
            codeWriter.methodVisitor.visitLabel(label2)
        }else {
            run()
            codeWriter.methodVisitor.visitLabel(label)
        }
    }
    registerEmitterInstruction(InstructionTypes.ALIGN_POSITION) {
        val endNew = codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java)
        val swizzle = instruction.get<SwizzleArgument>("swizzle")
        load("position")
        codeWriter.writeMapped(Vec3_x, true)
        if(swizzle.x) codeWriter.writeMapped(Math_floor)
        load("position")
        codeWriter.writeMapped(Vec3_y, true)
        if(swizzle.y) codeWriter.writeMapped(Math_floor)
        load("position")
        codeWriter.writeMapped(Vec3_z, true)
        if(swizzle.z) codeWriter.writeMapped(Math_floor)
        endNew()
        runStored("result", "\$Position", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_GAMEMODE) {
        load("entity")
        codeWriter.castType(ServerPlayer::class.java)
        codeWriter.writeMapped(ServerPlayer_gameMode, true)
        codeWriter.writeMapped(ServerPlayerGameMode_getGameModeForPlayer)
        when(instruction.get<Gamemode>("gamemode")) {
            Gamemode.SURVIVAL -> codeWriter.writeMapped(GameType_SURVIVAL, true)
            Gamemode.CREATIVE -> codeWriter.writeMapped(GameType_CREATIVE, true)
            Gamemode.ADVENTURE -> codeWriter.writeMapped(GameType_ADVENTURE, true)
            Gamemode.SPECTATOR -> codeWriter.writeMapped(GameType_SPECTATOR, true)
        }
        val label = Label()
        codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IF_ACMPNE else Opcodes.IF_ACMPEQ, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }
    registerEmitterInstruction(InstructionTypes.SET_GAMEMODE) {
        load("entity")
        codeWriter.castType(ServerPlayer::class.java)
        when(instruction.get<Gamemode>("gamemode")) {
            Gamemode.SURVIVAL -> codeWriter.writeMapped(GameType_SURVIVAL, true)
            Gamemode.CREATIVE -> codeWriter.writeMapped(GameType_CREATIVE, true)
            Gamemode.ADVENTURE -> codeWriter.writeMapped(GameType_ADVENTURE, true)
            Gamemode.SPECTATOR -> codeWriter.writeMapped(GameType_SPECTATOR, true)
        }
        codeWriter.writeMapped(ServerPlayer_setGameMode)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_TEAM) {
        if(instruction["negated"]) {
            load("entity")
            codeWriter.writeMapped(Entity_getTeam)
            val label = Label()
            val endIf = codeWriter.executeIf(Opcodes.IFNULL)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label)
            endIf()
            codeWriter.writeString(instruction["team"])
            load("entity")
            codeWriter.writeMapped(Entity_getTeam)
            codeWriter.writeMapped(Team_getName)
            codeWriter.writeMapped(String_equals)
            val endIf2 = codeWriter.executeIf(Opcodes.IFNE)
            codeWriter.methodVisitor.visitLabel(label)
            run()
            endIf2()
        }else {
            val label = Label()
            load("entity")
            codeWriter.writeMapped(Entity_getTeam)
            val endIf = codeWriter.executeIf(Opcodes.IFNONNULL)
            codeWriter.writeString(instruction["team"])
            load("entity")
            codeWriter.writeMapped(Entity_getTeam)
            codeWriter.writeMapped(Team_getName)
            codeWriter.writeMapped(String_equals)
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, label)
            endIf()
            val after = Label()
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, after)
            codeWriter.methodVisitor.visitLabel(label)
            run()
            codeWriter.methodVisitor.visitLabel(after)
        }
    }
    registerEmitterInstruction(InstructionTypes.HAS_TEAM) {
        load("entity")
        codeWriter.writeMapped(Entity_getTeam)
        val endIf = codeWriter.executeIf(if(!instruction.get<Boolean>("negated")) Opcodes.IFNULL else Opcodes.IFNONNULL)
        run()
        endIf()
    }
    registerEmitterInstruction(InstructionTypes.GET_ATTRIBUTE_INSTANCE) {
        load("entity")
        codeWriter.castType(LivingEntity::class.java)
        codeWriter.writeMapped(LivingEntity_getAttributes)
        fieldStorage.loadAttribute(codeWriter, instruction["attribute"])
        codeWriter.writeMapped(AttributeMap_getInstance)
        runStored("instance", "\$AttributeInstance", AttributeInstance::class.java)
    }
    registerEmitterInstruction(InstructionTypes.SET_ATTRIBUTE_BASE)  {
        load("instance")
        codeWriter.writeDouble(instruction["value"])
        codeWriter.writeMapped(AttributeInstance_setBaseValue)
    }
}