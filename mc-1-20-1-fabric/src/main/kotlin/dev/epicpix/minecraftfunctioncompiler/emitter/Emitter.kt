package dev.epicpix.minecraftfunctioncompiler.emitter

import dev.epicpix.minecraftfunctioncompiler.data.*
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
        codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_getEntity)
        runStored("entity", "\$GetCurrentEntity", Entity::class.java)
    }
    registerEmitterInstruction(InstructionTypes.ADD_ENTITY_TAG) {
        load("entity")
        codeWriter.writeString(instruction["tag"])
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_addTag)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.REMOVE_ENTITY_TAG) {
        load("entity")
        codeWriter.writeString(instruction["tag"])
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_removeTag)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TAG) {
        codeWriter.callSet_contains {
            load("entity")
            codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTags)
            codeWriter.writeString(instruction["tag"])
        }

        val label = Label()
        codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TAGS) {
        codeWriter.callSet_size {
            load("entity")
            codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTags)
        }

        val label = Label()
        codeWriter.methodVisitor.visitJumpInsn(if(!instruction.get<Boolean>("negated")) Opcodes.IFEQ else Opcodes.IFNE, label)
        run()
        codeWriter.methodVisitor.visitLabel(label)
    }
    registerEmitterInstruction(InstructionTypes.FOR_EACH_PLAYER) {
        val uidLocal = codeWriter.generateUniqueLocal("\$Players")
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_getServer)
        codeWriter.writeMapped(MappedUsageDefinitions.MinecraftServer_getPlayerList)
        codeWriter.writeMapped(MappedUsageDefinitions.PlayerList_getPlayers)
        codeWriter.storeLocal(uidLocal, List::class.java) {
            codeWriter.createListForEachLoop(uidLocal, Entity::class.java) {
                runStored("entity", "\$Entity", Entity::class.java)
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.FOR_EACH_ENTITY) {
        val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_getServer)
        codeWriter.writeMapped(MappedUsageDefinitions.MinecraftServer_getAllLevels)
        codeWriter.storeLocal(uidLocalLevels, Iterable::class.java) {
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) {
                val uidLocalEntities = codeWriter.generateUniqueLocal("\$Entities")
                codeWriter.callIterable_iterator { codeWriter.writeMapped(MappedUsageDefinitions.ServerLevel_getAllEntities) }
                codeWriter.storeLocal(uidLocalEntities, Iterator::class.java) {
                    codeWriter.createIteratorForEachLoop(uidLocalEntities, Entity::class.java, true) {
                        runStored("entity", "\$Entity", Entity::class.java)
                    }
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.FOR_PLAYER) {
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_getServer)
        codeWriter.writeString(instruction["name"])
        codeWriter.writeMapped(MappedUsageDefinitions.PlayerList_getPlayerByName)
        runStored("entity", "\$Player", ServerPlayer::class.java)
    }
    registerEmitterInstruction(InstructionTypes.FOR_ENTITY) {
        codeWriter.loadLocal("CommandSourceStack")
        codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_getServer)
        codeWriter.writeMapped(MappedUsageDefinitions.MinecraftServer_getAllLevels)
        val uidLocalLevels = codeWriter.generateUniqueLocal("\$Levels")
        codeWriter.storeLocal(uidLocalLevels, Iterable::class.java) {
            codeWriter.createIterableForEachLoop(uidLocalLevels, ServerLevel::class.java, true) {
                fieldStorage.loadUUID(codeWriter, instruction["uuid"])
                codeWriter.writeMapped(MappedUsageDefinitions.ServerLevel_getEntity)
                val uidLocal = codeWriter.generateUniqueLocal("\$Entity")
                storeCreate("entity", uidLocal, Entity::class.java) {
                    codeWriter.loadLocal(uidLocal)
                    codeWriter.executeIf(Opcodes.IFNONNULL) { run() }
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.FOR_SCORE_HOLDERS) {
        codeWriter.newObject(ArrayList::class.java, Collection::class.java) {
            codeWriter.callMap_keySet {
                codeWriter.loadScoreboard_playerScores {
                    fieldStorage.loadScoreboard(codeWriter)
                }
            }
        }

        val uidLocal = codeWriter.generateUniqueLocal("\$Array")
        codeWriter.storeLocal(uidLocal, ArrayList::class.java) {
            if(!instruction.get<Boolean>("single")) {
                codeWriter.createListForEachLoop(uidLocal, String::class.java) {
                    runStored("local", "\$Name", String::class.java)
                }
            }else {
                codeWriter.callArrayList_size { codeWriter.loadLocal(uidLocal) }
                codeWriter.executeIf(Opcodes.IFNE) {
                    codeWriter.callArrayList_get {
                        codeWriter.loadLocal(uidLocal)
                        codeWriter.writeInt(0)
                    }
                    codeWriter.castType(String::class.java)
                    runStored("local", "\$Name", String::class.java)
                }
            }
        }

    }
    registerEmitterInstruction(InstructionTypes.GET_SCOREBOARD_NAME) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_getScoreboardName)
        runStored("name", "\$ScoreboardName", String::class.java)
    }
    registerEmitterInstruction(InstructionTypes.SCORE_DATA) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        load("objective")
        codeWriter.writeMapped(if(instruction["autoCreate"]) MappedUsageDefinitions.Scoreboard_getOrCreatePlayerScore else MappedUsageDefinitions.Scoreboard_getScore)
        runStored("score", "\$ScoreData", Score::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_SCORE_VALUE) {
        load("score")
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
        runStored("scoreValue", "\$ScoreValue", Int::class.javaPrimitiveType!!)
    }
    registerEmitterInstruction(InstructionTypes.SET_SCORE) {
        load("score")
        load("value")
        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.ADD_SCORE) {
        load("score")
        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
        load("value")
        codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.REMOVE_SCORE) {
        load("score")
        codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
        load("value")
        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
    }
    registerEmitterInstruction(InstructionTypes.RUN_SCORE_OPERATION) {
        when(val operation = instruction.get<ScoreboardOperation>("operation")) {
            ScoreboardOperation.SET -> {
                load("target")
                load("source")
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
            }
            ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                when(operation) {
                    ScoreboardOperation.PLUS -> codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    ScoreboardOperation.MINUS -> {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    }
                    else -> codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                }
                codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
            }
            ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                load("target")
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                codeWriter.storeLocal(uniqBeh, Int::class.javaPrimitiveType!!) {
                    load("source")
                    val uniq = codeWriter.generateUniqueLocal("\$Score")
                    codeWriter.storeLocal(uniq, Score::class.java) {
                        codeWriter.loadLocal(uniq)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                        codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scopeId("exceptionScope")]!!.breakLabel)
                        codeWriter.loadLocal(uniq)
                        codeWriter.loadLocal(uniqBeh)
                        codeWriter.loadLocal(uniq)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                        if(operation == ScoreboardOperation.DIVIDE) codeWriter.callMath_floorDiv {}
                        else codeWriter.callMath_floorMod {}
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
                    }
                }
            }
            ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                if(operation == ScoreboardOperation.MIN) codeWriter.callMath_min {}
                else codeWriter.callMath_max {}
                codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
            }
            ScoreboardOperation.SWAP -> {
                load("target")
                val targetId = codeWriter.generateUniqueLocal("\$Score")
                codeWriter.storeLocal(targetId, Score::class.java) {
                    load("source")
                    val sourceId = codeWriter.generateUniqueLocal("\$Score")
                    codeWriter.storeLocal(sourceId, Score::class.java) {
                        codeWriter.loadLocal(sourceId)
                        codeWriter.loadLocal(targetId)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                        codeWriter.loadLocal(targetId)
                        codeWriter.loadLocal(sourceId)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
                        codeWriter.writeMapped(MappedUsageDefinitions.Score_setScore)
                    }
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.GET_OBJECTIVE) {
        fieldStorage.loadScoreboard(codeWriter)
        codeWriter.writeString(instruction["name"])
        codeWriter.writeMapped(MappedUsageDefinitions.Scoreboard_getObjective)
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
                codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_withEntity)
            }
            if(instruction.get<LocationData?>("world") != null) {
                load("world")
                codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_withLevel)
            }
            if(instruction.get<LocationData?>("position") != null) {
                load("position")
                codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_withPosition)
            }
            if(instruction.get<LocationData?>("rotation") != null) {
                load("rotation")
                codeWriter.writeMapped(MappedUsageDefinitions.CommandSourceStack_withRotation)
            }
            if(compiledFunctions.contains(func)) {
                codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}".replace('.', '$'), "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
            }else {
                codeWriter.writeNull()
                codeWriter.writeMapped(MappedUsageDefinitions.ServerFunctionManager_execute)
            }
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
    }
    registerEmitterInstruction(InstructionTypes.KILL_ENTITY) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_kill)
    }
    registerEmitterInstruction(InstructionTypes.RESET_SCORE) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        load("objective")
        codeWriter.writeMapped(MappedUsageDefinitions.Scoreboard_resetPlayerScore)
    }
    registerEmitterInstruction(InstructionTypes.RESET_PLAYER_SCORE) {
        fieldStorage.loadScoreboard(codeWriter)
        load("name")
        codeWriter.writeNull()
        codeWriter.writeMapped(MappedUsageDefinitions.Scoreboard_resetPlayerScore)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_RANGE) {
        load("score")
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)
        val range = instruction.get<IntRangeArgument>("range")
        val negate = instruction.get<Boolean>("negate")
        if(range.minimum != null && range.maximum == null) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPGE else Opcodes.IF_ICMPLT) { run() }
        }else if(range.minimum == null && range.maximum != null) {
            codeWriter.writeInt(range.maximum!!)
            codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPLE else Opcodes.IF_ICMPGT) { run() }
        }else if(range.minimum != null && range.minimum == range.maximum) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(if(!negate) Opcodes.IF_ICMPEQ else Opcodes.IF_ICMPNE) { run() }
        }else if(range.minimum != null) {
            val uid = codeWriter.generateUniqueLocal("\$Score")
            codeWriter.storeLocal(uid, Int::class.javaPrimitiveType!!) {
                if(!negate) {
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(range.minimum!!)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(range.maximum!!)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() }
                    }
                } else {
                    val skip = Label()
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(range.minimum!!)
                    codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                        codeWriter.loadLocal(uid)
                        codeWriter.writeInt(range.maximum!!)
                        codeWriter.executeIf(Opcodes.IF_ICMPLE) {
                            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, skip)
                        }
                    }
                    run()
                    codeWriter.methodVisitor.visitLabel(skip)
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_COMPARE) {
        load("source")
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)

        load("target")
        codeWriter.writeMapped(MappedUsageDefinitions.Score_getScore)

        codeWriter.executeIf(when(instruction.get<ScoreboardCompareOperation>("compareOperation")) {
            ScoreboardCompareOperation.LT -> Opcodes.IF_ICMPLT
            ScoreboardCompareOperation.LE -> Opcodes.IF_ICMPLE
            ScoreboardCompareOperation.EQ -> Opcodes.IF_ICMPEQ
            ScoreboardCompareOperation.NE -> Opcodes.IF_ICMPNE
            ScoreboardCompareOperation.GE -> Opcodes.IF_ICMPGE
            ScoreboardCompareOperation.GT -> Opcodes.IF_ICMPGT
        }) { run() }
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_WORLD) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_level)
        codeWriter.castType(ServerLevel::class.java)
        runStored("world", "\$World", ServerLevel::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_POSITION) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_position)
        runStored("position", "\$Position", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.GET_ENTITY_ROTATION) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_getRotationVector)
        runStored("rotation", "\$Rotation", Vec2::class.java)
    }
    registerEmitterInstruction(InstructionTypes.APPLY_POSITION_WORLD) {
        codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java) {
            val pos = instruction.get<WorldCoordinates>("position")
            if(pos.isXRelative) {
                codeWriter.loadVec3_x { load("from") }
                if(pos.x != 0.0) {
                    codeWriter.writeDouble(pos.x)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                }
            } else {
                codeWriter.writeDouble(pos.x)
            }
            if(pos.isYRelative) {
                codeWriter.loadVec3_y { load("from") }
                if(pos.y != 0.0) {
                    codeWriter.writeDouble(pos.y)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                }
            } else {
                codeWriter.writeDouble(pos.y)
            }
            if(pos.isZRelative) {
                codeWriter.loadVec3_z { load("from") }
                if(pos.z != 0.0) {
                    codeWriter.writeDouble(pos.z)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                }
            } else {
                codeWriter.writeDouble(pos.z)
            }
        }
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
        codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java) {
            val swizzle = instruction.get<SwizzleArgument>("swizzle")
            codeWriter.loadVec3_x { load("position") }
            if(swizzle.x) codeWriter.callMath_floor {}
            codeWriter.loadVec3_y { load("position") }
            if(swizzle.y) codeWriter.callMath_floor {}
            codeWriter.loadVec3_z { load("position") }
            if(swizzle.z) codeWriter.callMath_floor {}
        }
        runStored("result", "\$Position", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_GAMEMODE) {
        codeWriter.loadServerPlayer_gameMode {
            load("entity")
            codeWriter.castType(ServerPlayer::class.java)
        }
        codeWriter.writeMapped(MappedUsageDefinitions.ServerPlayerGameMode_getGameModeForPlayer)
        when(instruction.get<Gamemode>("gamemode")) {
            Gamemode.SURVIVAL -> codeWriter.loadGameType_SURVIVAL()
            Gamemode.CREATIVE -> codeWriter.loadGameType_CREATIVE()
            Gamemode.ADVENTURE -> codeWriter.loadGameType_ADVENTURE()
            Gamemode.SPECTATOR -> codeWriter.loadGameType_SPECTATOR()
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
            Gamemode.SURVIVAL -> codeWriter.loadGameType_SURVIVAL()
            Gamemode.CREATIVE -> codeWriter.loadGameType_CREATIVE()
            Gamemode.ADVENTURE -> codeWriter.loadGameType_ADVENTURE()
            Gamemode.SPECTATOR -> codeWriter.loadGameType_SPECTATOR()
        }
        codeWriter.writeMapped(MappedUsageDefinitions.ServerPlayer_setGameMode)
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_TEAM) {
        if(instruction["negated"]) {
            load("entity")
            codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTeam)
            val label = Label()
            codeWriter.executeIf(Opcodes.IFNULL) {
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label)
            }
            codeWriter.callString_equals {
                codeWriter.writeString(instruction["team"])
                load("entity")
                codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTeam)
                codeWriter.writeMapped(MappedUsageDefinitions.Team_getName)
            }
            codeWriter.executeIf(Opcodes.IFNE) {
                codeWriter.methodVisitor.visitLabel(label)
                run()
            }
        }else {
            val label = Label()
            load("entity")
            codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTeam)
            codeWriter.executeIf(Opcodes.IFNONNULL) {
                codeWriter.callString_equals {
                    codeWriter.writeString(instruction["team"])
                    load("entity")
                    codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTeam)
                    codeWriter.writeMapped(MappedUsageDefinitions.Team_getName)
                }
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFNE, label)
            }
            val after = Label()
            codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, after)
            codeWriter.methodVisitor.visitLabel(label)
            run()
            codeWriter.methodVisitor.visitLabel(after)
        }
    }
    registerEmitterInstruction(InstructionTypes.HAS_TEAM) {
        load("entity")
        codeWriter.writeMapped(MappedUsageDefinitions.Entity_getTeam)
        codeWriter.executeIf(if(!instruction.get<Boolean>("negated")) Opcodes.IFNULL else Opcodes.IFNONNULL) { run() }
    }
    registerEmitterInstruction(InstructionTypes.GET_ATTRIBUTE_INSTANCE) {
        load("entity")
        codeWriter.castType(LivingEntity::class.java)
        codeWriter.writeMapped(MappedUsageDefinitions.LivingEntity_getAttributes)
        fieldStorage.loadAttribute(codeWriter, instruction["attribute"])
        codeWriter.writeMapped(MappedUsageDefinitions.AttributeMap_getInstance)
        runStored("instance", "\$AttributeInstance", AttributeInstance::class.java)
    }
    registerEmitterInstruction(InstructionTypes.SET_ATTRIBUTE_BASE)  {
        load("instance")
        codeWriter.writeDouble(instruction["value"])
        codeWriter.writeMapped(MappedUsageDefinitions.AttributeInstance_setBaseValue)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ROTATION) {
        val yaw = instruction.get<FloatRangeArgument?>("yaw")
        if(yaw != null) {
            codeWriter.loadVec2_x { load("rotation") }
            checkFloatRange(yaw) {
                val pitch = instruction.get<FloatRangeArgument?>("pitch")
                if(pitch != null) {
                    codeWriter.loadVec2_y { load("rotation") }
                    checkFloatRange(pitch) { run() }
                    return@checkFloatRange
                }
                run()
            }
            return@registerEmitterInstruction
        }
        val pitch = instruction.get<FloatRangeArgument?>("pitch")
        if(pitch != null) {
            codeWriter.loadVec2_y { load("rotation") }
            checkFloatRange(pitch) { run() }
            return@registerEmitterInstruction
        }
        run()
    }
}
