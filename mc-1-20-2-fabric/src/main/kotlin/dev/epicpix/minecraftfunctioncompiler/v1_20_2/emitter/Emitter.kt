package dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter

import dev.epicpix.minecraftfunctioncompiler.data.*
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterHelper.checkFloatRange
import dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMath_floor
import dev.epicpix.minecraftfunctioncompiler.emitter.registerEmitterInstruction
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes
import dev.epicpix.minecraftfunctioncompiler.il.LocationData
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.data.RegistryMapping
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter.MappedUsageDefinitionsExtensions.*
import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.phys.Vec3
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

fun registerInstructionEmitters() {
    registerEmitterInstruction(InstructionTypes.CALL_FUNCTIONS) {
        for(func in instruction.get<List<DataLocation>>("functions")) {
            if(!compiledFunctions.contains(func)) {
                fieldStorage.loadServerFunctionManager(codeWriter)
                loadServerFunctionManager_context(codeWriter)
                fieldStorage.loadServerFunctionManager(codeWriter)
                codeWriter.writeNull()
                storeServerFunctionManager_context(codeWriter)
                fieldStorage.loadServerFunctionManager(codeWriter)
                fieldStorage.loadFunction(codeWriter, func)
            }
            codeWriter.loadLocal("static\$CommandSourceStack")
            if(instruction.get<LocationData<*>?>("entity") != null) {
                load("entity")
                callCommandSourceStack_withEntity(codeWriter)
            }
            if(instruction.get<LocationData<*>?>("world") != null) {
                load("world")
                callCommandSourceStack_withLevel(codeWriter)
            }
            if(instruction.get<LocationData<*>?>("position") != null) {
                load("position")
                callCommandSourceStack_withPosition(codeWriter)
            }
            if(instruction.get<LocationData<*>?>("rotation") != null) {
                load("rotation")
                callCommandSourceStack_withRotation(codeWriter)
            }
            if(compiledFunctions.contains(func)) {
                codeWriter.methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "function/${func.namespace}/${func.path}".replace('.', '$'), "run", "(${CommandSourceStack::class.java.descriptorString()})I", false)
                codeWriter.methodVisitor.visitInsn(Opcodes.POP)
            }else {
                codeWriter.writeNull()
                codeWriter.writeNull()
                callServerFunctionManager_execute(codeWriter)
                codeWriter.methodVisitor.visitInsn(Opcodes.POP)
                fieldStorage.loadServerFunctionManager(codeWriter)
                codeWriter.methodVisitor.visitInsn(Opcodes.SWAP)
                storeServerFunctionManager_context(codeWriter)
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_IN_SCORE_RANGE) {
        load("score")
        callScore_getScore(codeWriter)
        val range = instruction.get<IntRangeArgument>("range")
        if(range.minimum != null && range.maximum == null) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPGE) { run() }
        }else if(range.minimum == null && range.maximum != null) {
            codeWriter.writeInt(range.maximum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() }
        }else if(range.minimum != null && range.minimum == range.maximum) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPEQ) { run() }
        }else if(range.minimum != null) {
            val uid = codeWriter.generateUniqueLocal()
            codeWriter.storeLocal(uid, Int::class.javaPrimitiveType!!) {
                codeWriter.loadLocal(uid)
                codeWriter.writeInt(range.minimum!!)
                codeWriter.executeIf(Opcodes.IF_ICMPGE) {
                    codeWriter.loadLocal(uid)
                    codeWriter.writeInt(range.maximum!!)
                    codeWriter.executeIf(Opcodes.IF_ICMPLE) { run() }
                }
            }
        }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_NOT_IN_SCORE_RANGE) {
        load("score")
        callScore_getScore(codeWriter)
        val range = instruction.get<IntRangeArgument>("range")
        if(range.minimum != null && range.maximum == null) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPLT) { run() }
        }else if(range.minimum == null && range.maximum != null) {
            codeWriter.writeInt(range.maximum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPGT) { run() }
        }else if(range.minimum != null && range.minimum == range.maximum) {
            codeWriter.writeInt(range.minimum!!)
            codeWriter.executeIf(Opcodes.IF_ICMPNE) { run() }
        }else if(range.minimum != null) {
            val uid = codeWriter.generateUniqueLocal()
            codeWriter.storeLocal(uid, Int::class.javaPrimitiveType!!) {
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
    registerEmitterInstruction(InstructionTypes.CHECK_SCORE_COMPARE) {
        load("source")
        callScore_getScore(codeWriter)
        load("target")
        callScore_getScore(codeWriter)

        codeWriter.executeIf(when(instruction.get<ScoreboardCompareOperation>("compareOperation")!!) {
            ScoreboardCompareOperation.LT -> Opcodes.IF_ICMPLT
            ScoreboardCompareOperation.LE -> Opcodes.IF_ICMPLE
            ScoreboardCompareOperation.EQ -> Opcodes.IF_ICMPEQ
            ScoreboardCompareOperation.NE -> Opcodes.IF_ICMPNE
            ScoreboardCompareOperation.GE -> Opcodes.IF_ICMPGE
            ScoreboardCompareOperation.GT -> Opcodes.IF_ICMPGT
        }) { run() }
    }
    registerEmitterInstruction(InstructionTypes.APPLY_POSITION_WORLD) {
        codeWriter.newObject(Vec3::class.java, Double::class.java, Double::class.java, Double::class.java) {
            val pos = instruction.get<WorldCoordinates>("position")
            if(pos.isXRelative) {
                load("from")
                loadVec3_x(codeWriter)
                if(pos.x != 0.0) {
                    codeWriter.writeDouble(pos.x)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                }
            } else {
                codeWriter.writeDouble(pos.x)
            }
            if(pos.isYRelative) {
                load("from")
                loadVec3_y(codeWriter)
                if(pos.y != 0.0) {
                    codeWriter.writeDouble(pos.y)
                    codeWriter.methodVisitor.visitInsn(Opcodes.DADD)
                }
            } else {
                codeWriter.writeDouble(pos.y)
            }
            if(pos.isZRelative) {
                load("from")
                loadVec3_z(codeWriter)
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
            load("position")
            loadVec3_x(codeWriter);
            if(swizzle.x) callMath_floor(codeWriter)
            load("position")
            loadVec3_y(codeWriter);
            if(swizzle.y) callMath_floor(codeWriter)
            load("position")
            loadVec3_z(codeWriter);
            if(swizzle.z) callMath_floor(codeWriter)
        }
        runStored("result", Vec3::class.java)
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ROTATION_YAW) {
        load("rotation")
        loadVec2_x(codeWriter)
        checkFloatRange(this, instruction.get("yaw")) { run() }
    }
    registerEmitterInstruction(InstructionTypes.CHECK_ROTATION_PITCH) {
        load("rotation")
        loadVec2_y(codeWriter)
        checkFloatRange(this, instruction.get("pitch")) { run() }
    }
}
