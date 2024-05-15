package dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter

import dev.epicpix.minecraftfunctioncompiler.data.*
import dev.epicpix.minecraftfunctioncompiler.emitter.*
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardCompareOperation
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardOperation
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.data.mapEntityTypeClass
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Score
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun registerInstructionEmitters() {
    registerEmitterInstruction(InstructionTypes.SCORE_DATA) {
        if(instruction["autoCreate"]) {
            codeWriter.callScoreboard_getOrCreatePlayerScore {
                fieldStorage.loadScoreboard(codeWriter)
                load("name")
                load("objective")
            }
        }else {
            codeWriter.callScore_getScore {
                fieldStorage.loadScoreboard(codeWriter)
                load("name")
                load("objective")
            }
        }
        runStored("score", "\$ScoreData", Score::class.java)
    }
    registerEmitterInstruction(InstructionTypes.RUN_SCORE_OPERATION) {
        when(val operation = instruction.get<ScoreboardOperation>("operation")) {
            ScoreboardOperation.SET -> {
                codeWriter.callScore_setScore {
                    codeWriter.callScore_getScore {
                        load("target")
                        load("source")
                    }
                }
            }
            ScoreboardOperation.PLUS, ScoreboardOperation.MINUS, ScoreboardOperation.MULTIPLY -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.callScore_getScore {  }
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.callScore_getScore {  }
                when(operation) {
                    ScoreboardOperation.PLUS -> codeWriter.methodVisitor.visitInsn(Opcodes.IADD)
                    ScoreboardOperation.MINUS -> {
                        codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                        codeWriter.methodVisitor.visitInsn(Opcodes.ISUB)
                    }
                    else -> codeWriter.methodVisitor.visitInsn(Opcodes.IMUL)
                }
                codeWriter.callScore_setScore {  }
            }
            ScoreboardOperation.DIVIDE, ScoreboardOperation.MODULO -> {
                load("target")
                codeWriter.callScore_getScore {  }
                val uniqBeh = codeWriter.generateUniqueLocal("\$Score")
                codeWriter.storeLocal(uniqBeh, Int::class.javaPrimitiveType!!) {
                    load("source")
                    val uniq = codeWriter.generateUniqueLocal("\$Score")
                    codeWriter.storeLocal(uniq, Score::class.java) {
                        codeWriter.loadLocal(uniq)
                        codeWriter.callScore_getScore {  }
                        codeWriter.methodVisitor.visitJumpInsn(Opcodes.IFEQ, newScopes[scopeId("exceptionScope")]!!)
                        codeWriter.loadLocal(uniq)
                        codeWriter.loadLocal(uniqBeh)
                        codeWriter.loadLocal(uniq)
                        codeWriter.callScore_getScore {  }
                        if(operation == ScoreboardOperation.DIVIDE) codeWriter.callMath_floorDiv {}
                        else codeWriter.callMath_floorMod {}
                        codeWriter.callScore_setScore {  }
                    }
                }
            }
            ScoreboardOperation.MIN, ScoreboardOperation.MAX -> {
                load("target")
                codeWriter.methodVisitor.visitInsn(Opcodes.DUP)
                load("source")
                codeWriter.callScore_getScore {  }
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                codeWriter.callScore_getScore {  }
                if(operation == ScoreboardOperation.MIN) codeWriter.callMath_min {}
                else codeWriter.callMath_max {}
                codeWriter.callScore_setScore {  }
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
                        codeWriter.callScore_getScore {  }
                        codeWriter.loadLocal(targetId)
                        codeWriter.loadLocal(sourceId)
                        codeWriter.callScore_getScore {  }
                        codeWriter.callScore_setScore {  }
                        codeWriter.callScore_setScore {  }
                    }
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.CALL_FUNCTIONS) {
        for(func in instruction.get<List<DataLocation>>("functions")) {
            if(!compiledFunctions.contains(func)) {
                fieldStorage.loadServerFunctionManager(codeWriter)
                fieldStorage.loadFunction(codeWriter, func)
            }
            codeWriter.loadLocal("static\$CommandSourceStack")
            if(instruction.get<LocationData?>("entity") != null) {
                codeWriter.callCommandSourceStack_withEntity { load("entity") }
            }
            if(instruction.get<LocationData?>("world") != null) {
                codeWriter.callCommandSourceStack_withLevel { load("world") }
            }
            if(instruction.get<LocationData?>("position") != null) {
                codeWriter.callCommandSourceStack_withPosition { load("position") }
            }
            if(instruction.get<LocationData?>("rotation") != null) {
                codeWriter.callCommandSourceStack_withRotation { load("rotation") }
            }
            if(compiledFunctions.contains(func)) {
                codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}".replace('.', '$'), "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
            }else {
                codeWriter.writeNull()
                codeWriter.callServerFunctionManager_execute {  }
            }
            codeWriter.methodVisitor.visitInsn(Opcodes.POP)
        }
    }
    registerEmitterInstruction(InstructionTypes.RESET_SCORE) {
        codeWriter.callScoreboard_resetPlayerScore {
            fieldStorage.loadScoreboard(codeWriter)
            load("name")
            load("objective")
        }
    }
    registerEmitterInstruction(InstructionTypes.RESET_PLAYER_SCORE) {
        codeWriter.callScoreboard_resetPlayerScore {
            fieldStorage.loadScoreboard(codeWriter)
            load("name")
            codeWriter.writeNull()
        }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_RANGE) {
        codeWriter.callScore_getScore { load("score") }
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
        codeWriter.callScore_getScore { load("source") }
        codeWriter.callScore_getScore { load("target") }

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
        codeWriter.callEntity_level { load("entity") }
        codeWriter.castType(ServerLevel::class.java)
        runStored("world", "\$World", ServerLevel::class.java)
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
        codeWriter.callServerPlayerGameMode_getGameModeForPlayer {
            codeWriter.loadServerPlayer_gameMode {
                load("entity")
                codeWriter.castType(ServerPlayer::class.java)
            }
        }
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
        codeWriter.callServerPlayer_setGameMode {
            load("entity")
            codeWriter.castType(ServerPlayer::class.java)
            when(instruction.get<Gamemode>("gamemode")) {
                Gamemode.SURVIVAL -> codeWriter.loadGameType_SURVIVAL()
                Gamemode.CREATIVE -> codeWriter.loadGameType_CREATIVE()
                Gamemode.ADVENTURE -> codeWriter.loadGameType_ADVENTURE()
                Gamemode.SPECTATOR -> codeWriter.loadGameType_SPECTATOR()
            }
        }
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_TEAM) {
        if(instruction["negated"]) {
            codeWriter.callEntity_getTeam { load("entity") }
            val label = Label()
            codeWriter.executeIf(Opcodes.IFNULL) {
                codeWriter.methodVisitor.visitJumpInsn(Opcodes.GOTO, label)
            }
            codeWriter.callString_equals {
                codeWriter.writeString(instruction["team"])
                codeWriter.callTeam_getName {
                    codeWriter.callEntity_getTeam { load("entity") }
                }
            }
            codeWriter.executeIf(Opcodes.IFNE) {
                codeWriter.methodVisitor.visitLabel(label)
                run()
            }
        }else {
            val label = Label()
            codeWriter.callEntity_getTeam { load("entity") }
            codeWriter.executeIf(Opcodes.IFNONNULL) {
                codeWriter.callString_equals {
                    codeWriter.writeString(instruction["team"])
                    codeWriter.callTeam_getName {
                        codeWriter.callEntity_getTeam { load("entity") }
                    }
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
        codeWriter.callEntity_getTeam { load("entity") }
        codeWriter.executeIf(if(!instruction.get<Boolean>("negated")) Opcodes.IFNULL else Opcodes.IFNONNULL) { run() }
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
