package dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter

import dev.epicpix.minecraftfunctioncompiler.data.*
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterHelper.checkFloatRange
import dev.epicpix.minecraftfunctioncompiler.emitter.callMath_floor
import dev.epicpix.minecraftfunctioncompiler.emitter.registerEmitterInstruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.il.ScoreboardCompareOperation
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.data.RegistryMapping
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun registerInstructionEmitters() {
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
                codeWriter.methodVisitor.visitInsn(Opcodes.POP)
            }else {
                codeWriter.callServerFunctionManager_execute {  }
            }
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
            val uid = codeWriter.generateUniqueLocal()
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

        codeWriter.executeIf(when(instruction.get<ScoreboardCompareOperation>("compareOperation")!!) {
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
        runStored("world", ServerLevel::class.java)
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
        runStored("to", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ENTITY_TYPES) {
        val label = Label()
        for(res in instruction.get<List<DataLocation>>("types")) {
            load("entity")
            codeWriter.methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, RegistryMapping.mapEntityTypeClass(res)?.descriptorString()?.run { substring(1, length - 1) })
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
        runStored("result", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_GAMEMODE) {
        codeWriter.callServerPlayerGameMode_getGameModeForPlayer {
            codeWriter.loadServerPlayer_gameMode {
                load("entity")
                codeWriter.castType(ServerPlayer::class.java)
            }
        }
        when(instruction.get<Gamemode>("gamemode")!!) {
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
            when(instruction.get<Gamemode>("gamemode")!!) {
                Gamemode.SURVIVAL -> codeWriter.loadGameType_SURVIVAL()
                Gamemode.CREATIVE -> codeWriter.loadGameType_CREATIVE()
                Gamemode.ADVENTURE -> codeWriter.loadGameType_ADVENTURE()
                Gamemode.SPECTATOR -> codeWriter.loadGameType_SPECTATOR()
            }
        }
        codeWriter.methodVisitor.visitInsn(Opcodes.POP)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ROTATION_YAW) {
        codeWriter.loadVec2_x { load("rotation") }
        checkFloatRange(this, instruction.get("yaw")) { run() }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ROTATION_PITCH) {
        codeWriter.loadVec2_y { load("rotation") }
        checkFloatRange(this, instruction.get("pitch")) { run() }
    }
}
