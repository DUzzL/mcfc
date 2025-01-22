package dev.epicpix.minecraftfunctioncompiler.v1_21.emitter;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Type;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callMath_floor;
import static dev.epicpix.minecraftfunctioncompiler.v1_21.emitter.MappedUsageDefinitionsExtensions.*;

public final class Emitter {
    private Emitter() {}

    private static final InstructionTarget VEC3_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(Vec3.class, double.class, double.class, double.class);
    private static final InstructionTarget VEC2_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(Vec2.class, float.class, float.class);

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            if(context.instruction().type() == InstructionTypes.LOAD_MACRO) {
                var template = context.instruction().<MacroTemplate<String>>get("macroTemplate");
                var macroContent = template.writeBytecode(context);
                context.store("string", macroContent);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.MAKE_COMMAND_SOURCE_STACK) {
                var commandSourceStack = context.load("from");
                if(context.instruction().get("entity") != null) {
                    commandSourceStack = callCommandSourceStack_withEntity(codeWriter, commandSourceStack, context.load("entity"));
                }
                if(context.instruction().get("world") != null) {
                    commandSourceStack = callCommandSourceStack_withLevel(codeWriter, commandSourceStack, context.load("world"));
                }
                if(context.instruction().get("position") != null) {
                    commandSourceStack = callCommandSourceStack_withPosition(codeWriter, commandSourceStack, context.load("position"));
                }
                if(context.instruction().get("rotation") != null) {
                    commandSourceStack = callCommandSourceStack_withRotation(codeWriter, commandSourceStack, context.load("rotation"));
                }
                if(context.instruction().get("anchor") != null) {
                    var anchor = context.instruction().<LocationAnchor>get("anchor");
                    var anchorReg = anchor == LocationAnchor.EYES ? loadAnchor_EYES(codeWriter) : loadAnchor_FEET(codeWriter);
                    commandSourceStack = callCommandSourceStack_withAnchor(codeWriter, commandSourceStack, anchorReg);
                }
                context.store("to", commandSourceStack);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CALL_FUNCTIONS) {
                for(var func : context.instruction().<List<DataLocation>>get("functions")) {
                    BytecodeRegister mctxtl = null;
                    BytecodeRegister mctx = null;
                    if(!context.compiledFunctions().contains(func)) {
                        mctxtl = loadCommands_CURRENT_EXECUTION_CONTEXT(codeWriter);
                        mctx = callThreadLocal_get(codeWriter, mctxtl);
                        callThreadLocal_set(codeWriter, mctxtl, new BytecodeValue.NullValue());
                    }
                    var commandSourceStack = context.load("stack");
                    if(context.compiledFunctions().contains(func)) {
                        var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)) : new BytecodeValue.NullValue();
                        var target = new InstructionTarget.FunctionTarget(("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$'), "run", "(" + CommandSourceStack.class.descriptorString() + context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).getDescriptor() + ")J", true, false, false, true);
                        codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
                    }else {
                        var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
                        var function = context.fieldStorage().loadFunction(codeWriter, func);

                        if(context.instruction().get("arguments") != null) {
                            callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)));
                        }else {
                            callServerFunctionManager_execute(codeWriter, functionManager, function, commandSourceStack);
                        }
                        callThreadLocal_set(codeWriter, mctxtl, mctx);
                    }
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_SCORE_COMPARE) {
                var a = callScore_getScore(codeWriter, context.load("target"));
                var b = callScore_getScore(codeWriter, context.load("source"));

                codeWriter.conditionalJump(switch(context.instruction().<ScoreboardCompareOperation>get("compareOperation")) {
                    case LT -> JumpCondition.GE;
                    case LE -> JumpCondition.GT;
                    case EQ -> JumpCondition.NE;
                    case NE -> JumpCondition.EQ;
                    case GE -> JumpCondition.LT;
                    case GT -> JumpCondition.LE;
                }, a, b, context.mapLabel("failLabel"));
                return true;
            }
            if(context.instruction().type() == InstructionTypes.APPLY_LOCAL_POSITION_WORLD) {
                var pos = context.instruction().<WorldCoordinates>get("position");
                if(!pos.localCoordinates()) throw new UnsupportedOperationException("Cannot run APPLY_LOCAL_POSITION_WORLD with non-local coordinates");
                var rotX = loadVec2_x(codeWriter, context.load("fromRotation"));
                var rotY = loadVec2_y(codeWriter, context.load("fromRotation"));
                var fposX = loadVec3_x(codeWriter, context.load("fromPosition"));
                var fposY = loadVec3_y(codeWriter, context.load("fromPosition"));
                if(context.instruction().get("yOffset") != null) {
                    fposY = codeWriter.add(fposY, context.load("yOffset"));
                }
                var fposZ = loadVec3_z(codeWriter, context.load("fromPosition"));
                var posX = new BytecodeValue.DoubleValue(pos.x());
                var posY = new BytecodeValue.DoubleValue(pos.y());
                var posZ = new BytecodeValue.DoubleValue(pos.z());
                var calculated = callExtensionFunctions_calculateLocalCoordinates(codeWriter, rotX, rotY, fposX, fposY, fposZ, posX, posY, posZ);
                context.store("to", calculated);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.APPLY_POSITION_WORLD) {
                var pos = context.instruction().<WorldCoordinates>get("position");
                if(pos.localCoordinates()) throw new UnsupportedOperationException("Cannot run APPLY_POSITION_WORLD with local coordinates");
                BytecodeValue x = pos.isXRelative() ? codeWriter.add(loadVec3_x(codeWriter, context.load("from")), new BytecodeValue.DoubleValue(pos.x())) : new BytecodeValue.DoubleValue(pos.x());
                BytecodeValue y = pos.isYRelative() ? codeWriter.add(loadVec3_y(codeWriter, context.load("from")), new BytecodeValue.DoubleValue(pos.y())) : new BytecodeValue.DoubleValue(pos.y());
                BytecodeValue z = pos.isZRelative() ? codeWriter.add(loadVec3_z(codeWriter, context.load("from")), new BytecodeValue.DoubleValue(pos.z())) : new BytecodeValue.DoubleValue(pos.z());

                context.store("to", codeWriter.newObjectCreate(VEC3_INIT_TARGET, x, y, z));
                return true;
            }
            if(context.instruction().type() == InstructionTypes.APPLY_ROTATION_WORLD) {
                var rot = context.instruction().<RotationCoordinates>get("rotation");
                BytecodeValue yaw = rot.isYawRelative() ? codeWriter.add(loadVec2_x(codeWriter, context.load("from")), new BytecodeValue.FloatValue((float) rot.yaw())) : new BytecodeValue.FloatValue((float) rot.yaw());
                BytecodeValue pitch = rot.isPitchRelative() ? codeWriter.add(loadVec2_y(codeWriter, context.load("from")), new BytecodeValue.FloatValue((float) rot.pitch())) : new BytecodeValue.FloatValue((float) rot.pitch());

                context.store("to", codeWriter.newObjectCreate(VEC2_INIT_TARGET, yaw, pitch));
                return true;
            }
            if(context.instruction().type() == InstructionTypes.CHECK_ENTITY_TYPES) {
                var success = codeWriter.createLabel();
                for(var res : context.instruction().<List<DataLocation>>get("types")) {
                    var entity = context.load("entity");
                    var clazz = DataRegistries.ENTITY_CLASS.get(res).type();
                    if(context.instruction().<Boolean>get("negated")) {
                        codeWriter.instanceCheckJump(true, entity, clazz, context.mapLabel("failLabel"));
                    }else {
                        codeWriter.instanceCheckJump(true, entity, clazz, success);
                    }
                }
                if(!context.instruction().<Boolean>get("negated")) {
                    codeWriter.jump(context.mapLabel("failLabel"));
                }
                codeWriter.label(success);
                return true;
            }
            if(context.instruction().type() == InstructionTypes.ALIGN_POSITION) {
                var swizzle = context.instruction().<SwizzleArgument>get("swizzle");
                var x = swizzle.x() ? callMath_floor(codeWriter, loadVec3_x(codeWriter, context.load("position"))) : loadVec3_x(codeWriter, context.load("position"));
                var y = swizzle.y() ? callMath_floor(codeWriter, loadVec3_y(codeWriter, context.load("position"))) : loadVec3_y(codeWriter, context.load("position"));
                var z = swizzle.z() ? callMath_floor(codeWriter, loadVec3_z(codeWriter, context.load("position"))) : loadVec3_z(codeWriter, context.load("position"));

                context.store("result", codeWriter.newObjectCreate(VEC3_INIT_TARGET, x, y, z));
                return true;
            }
            if(context.instruction().type() == InstructionTypes.MERGE_POSITIONS) {
                var position = context.load("position");
                var x = context.instruction().get("x") != null ? context.load("x") : loadVec3_x(codeWriter, position);
                var y = context.instruction().get("y") != null ? context.load("y") : loadVec3_y(codeWriter, position);
                var z = context.instruction().get("z") != null ? context.load("z") : loadVec3_z(codeWriter, position);

                context.store("result", codeWriter.newObjectCreate(VEC3_INIT_TARGET, x, y, z));
                return true;
            }

            return false;
        });
    }
}
