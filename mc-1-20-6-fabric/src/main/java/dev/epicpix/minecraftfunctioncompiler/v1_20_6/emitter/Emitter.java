package dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.emitter.EmitterHelper.*;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMath_floor;
import static dev.epicpix.minecraftfunctioncompiler.v1_20_6.emitter.MappedUsageDefinitionsExtensions.*;

public final class Emitter {
    private Emitter() {}

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            var methodVisitor = codeWriter.methodVisitor();
            if(context.instruction().type() == InstructionTypes.CALL_FUNCTIONS) {
                for(var func : context.instruction().<List<DataLocation>>get("functions")) {
                    if(!context.compiledFunctions().contains(func)) {
                        context.fieldStorage().loadServerFunctionManager(codeWriter);
                        context.fieldStorage().loadFunction(codeWriter, func);
                    }
                    codeWriter.loadLocal("static$CommandSourceStack");
                    if(context.instruction().get("entity") != null) {
                        context.load("entity");
                        callCommandSourceStack_withEntity(codeWriter);
                    }
                    if(context.instruction().get("world") != null) {
                        context.load("world");
                        callCommandSourceStack_withLevel(codeWriter);
                    }
                    if(context.instruction().get("position") != null) {
                        context.load("position");
                        callCommandSourceStack_withPosition(codeWriter);
                    }
                    if(context.instruction().get("rotation") != null) {
                        context.load("rotation");
                        callCommandSourceStack_withRotation(codeWriter);
                    }
                    if(context.compiledFunctions().contains(func)) {
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, ("function/" + func.namespace() + "/" + func.path()).replace('.', '$'), "run", "(" + CommandSourceStack.class.descriptorString() + ")I", false);
                        methodVisitor.visitInsn(Opcodes.POP);
                    }else {
                        callServerFunctionManager_execute(codeWriter);
                    }
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_SCORE_COMPARE) {
                context.load("target");
                callScore_getScore(codeWriter);
                context.load("source");
                callScore_getScore(codeWriter);

                var endLabel = codeWriter.executeIfLabeled(switch(context.instruction().<ScoreboardCompareOperation>get("compareOperation")) {
                    case LT -> Opcodes.IF_ICMPLT;
                    case LE -> Opcodes.IF_ICMPLE;
                    case EQ -> Opcodes.IF_ICMPEQ;
                    case NE -> Opcodes.IF_ICMPNE;
                    case GE -> Opcodes.IF_ICMPGE;
                    case GT -> Opcodes.IF_ICMPGT;
                });
                context.run();
                methodVisitor.visitLabel(endLabel);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.APPLY_POSITION_WORLD) {
                codeWriter.newObjectCreate(Vec3.class);
                var pos = context.instruction().<WorldCoordinates>get("position");
                if(pos.isXRelative()) {
                    context.load("from");
                    loadVec3_x(codeWriter);
                    if(pos.x() != 0.0) {
                        codeWriter.writeDouble(pos.x());
                        methodVisitor.visitInsn(Opcodes.DADD);
                    }
                } else {
                    codeWriter.writeDouble(pos.x());
                }
                if(pos.isYRelative()) {
                    context.load("from");
                    loadVec3_y(codeWriter);
                    if(pos.y() != 0.0) {
                        codeWriter.writeDouble(pos.y());
                        methodVisitor.visitInsn(Opcodes.DADD);
                    }
                } else {
                    codeWriter.writeDouble(pos.y());
                }
                if(pos.isZRelative()) {
                    context.load("from");
                    loadVec3_z(codeWriter);
                    if(pos.z() != 0.0) {
                        codeWriter.writeDouble(pos.z());
                        methodVisitor.visitInsn(Opcodes.DADD);
                    }
                } else {
                    codeWriter.writeDouble(pos.z());
                }
                codeWriter.newObjectInit(Vec3.class, double.class, double.class, double.class);
                context.runStored("to", Vec3.class);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.APPLY_ROTATION_WORLD) {
                codeWriter.newObjectCreate(Vec2.class);
                var rot = context.instruction().<RotationCoordinates>get("rotation");
                if(rot.isYawRelative()) {
                    context.load("from");
                    loadVec2_x(codeWriter);
                    if(rot.yaw() != 0.0) {
                        codeWriter.writeFloat((float) rot.yaw());
                        methodVisitor.visitInsn(Opcodes.FADD);
                    }
                } else {
                    codeWriter.writeFloat((float) rot.yaw());
                }
                if(rot.isPitchRelative()) {
                    context.load("from");
                    loadVec2_y(codeWriter);
                    if(rot.pitch() != 0.0) {
                        codeWriter.writeFloat((float) rot.pitch());
                        methodVisitor.visitInsn(Opcodes.FADD);
                    }
                } else {
                    codeWriter.writeFloat((float) rot.pitch());
                }
                codeWriter.newObjectInit(Vec2.class, float.class, float.class);
                context.runStored("to", Vec2.class);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_ENTITY_TYPES) {
                var label = new Label();
                for(var res : context.instruction().<List<DataLocation>>get("types")) {
                    context.load("entity");
                    var desc = DataRegistries.ENTITY_CLASS.get(res).descriptorString();
                    methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, desc.substring(1, desc.length() - 1));
                    methodVisitor.visitJumpInsn(Opcodes.IFNE, label);
                }
                if(!context.instruction().<Boolean>get("negated")) {
                    var label2 = new Label();
                    methodVisitor.visitJumpInsn(Opcodes.GOTO, label2);
                    methodVisitor.visitLabel(label);
                    context.run();
                    methodVisitor.visitLabel(label2);
                }else {
                    context.run();
                    methodVisitor.visitLabel(label);
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes.ALIGN_POSITION) {
                codeWriter.newObjectCreate(Vec3.class);
                var swizzle = context.instruction().<SwizzleArgument>get("swizzle");
                context.load("position");
                loadVec3_x(codeWriter);
                if(swizzle.x()) callMath_floor(codeWriter);
                context.load("position");
                loadVec3_y(codeWriter);
                if(swizzle.y()) callMath_floor(codeWriter);
                context.load("position");
                loadVec3_z(codeWriter);
                if(swizzle.z()) callMath_floor(codeWriter);
                codeWriter.newObjectInit(Vec3.class, Double.class, Double.class, Double.class);
                context.runStored("result", Vec3.class);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_INT_RANGE) {
                context.load("value");
                checkIntRange(context, context.instruction().get("range"), EmitterContext::run);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_NOT_INT_RANGE) {
                context.load("value");
                checkIntRange(context, context.instruction().get("range"), EmitterContext::run);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_ROTATION_YAW) {
                context.load("rotation");
                loadVec2_x(codeWriter);
                checkFloatRange(context, context.instruction().get("yaw"), EmitterContext::run);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_ROTATION_PITCH) {
                context.load("rotation");
                loadVec2_y(codeWriter);
                checkFloatRange(context, context.instruction().get("pitch"), EmitterContext::run);
                return true;
            }

            return false;
        });
    }
}
