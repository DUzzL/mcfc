package dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter;

import dev.epicpix.minecraftfunctioncompiler.CommonConstants;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v1_20_2.CompiledCommandResultStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Type;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.v1_20_2.emitter.MappedUsageDefinitionsExtensions.*;

public final class Emitter {
    private Emitter() {}

    private static final InstructionTarget COMMAND_RESULT_STORE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompiledCommandResultStore.class);
    private static final InstructionTarget RUN_RESULT_FIELD_READ_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, "J", true, false);
    private static final InstructionTarget RUN_RESULT_FIELD_WRITE_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, "J", true, true);

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            if(context.instruction().type() == InstructionTypes2.LOAD_STRING_MACRO) {
                var template = context.instruction().<MacroTemplate<String>>get("macroTemplate");
                var macroContent = template.writeBytecode(context);
                context.store("result", macroContent);
                return true;
            }
            if(context.instruction().type() == InstructionTypes2.LOAD_INTEGER_MACRO) {
                var template = context.instruction().<MacroTemplate<Integer>>get("macroTemplate");
                var macroContent = template.writeBytecode(context);
                context.store("result", macroContent);
                return true;
            }
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS) {
                var functions = context.instruction().<List<DataLocation>>get("functions");
                var returnValueLocation = context.instruction().<LocationData<Long>>get("result");
                if(functions.isEmpty()) {
                    if(returnValueLocation != null) {
                        context.store("result", new BytecodeValue.LongValue(1L << 32));
                    }
                }else {
                    var post = codeWriter.createLabel();
                    codeWriter.writeTarget(RUN_RESULT_FIELD_WRITE_TARGET, new BytecodeValue.LongValue(2L << 32));
                    for(int i = 0; i < functions.size(); i++) {
                        var func = functions.get(i);
                        var commandSourceStack = context.load("stack");
                        BytecodeRegister returnValue = null;
                        var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)) : new BytecodeValue.NullValue();
                        if(context.compiledFunctions().contains(func)) {
                            var target = new InstructionTarget.FunctionTarget(("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$'), "run", "(" + CommandSourceStack.class.descriptorString() + context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).getDescriptor() + ")J", true, false, false, true);
                            returnValue = codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
                        }else {
                            var mctx = loadServerFunctionManager_context(codeWriter, context.fieldStorage().loadServerFunctionManager(codeWriter));
                            storeServerFunctionManager_context(codeWriter, context.fieldStorage().loadServerFunctionManager(codeWriter), new BytecodeValue.NullValue());
                            if(returnValueLocation != null) {
                                var store = codeWriter.newObjectCreate(COMMAND_RESULT_STORE_INIT_TARGET);
                                commandSourceStack = callCommandSourceStack_withCallback(codeWriter, commandSourceStack, store);
                            }
                            var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
                            var function = context.fieldStorage().loadFunction(codeWriter, func);

                            if(context.shouldIncludeProfilerSupport()) codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "enterOverhead", void.class, String.class), new BytecodeValue.StringValue(func.toString()));
                            callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, new BytecodeValue.NullValue(), nbtArgs);
                            if(context.shouldIncludeProfilerSupport()) codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "exit", void.class));
                            storeServerFunctionManager_context(codeWriter, context.fieldStorage().loadServerFunctionManager(codeWriter), mctx);
                            if(returnValueLocation != null) {
                                returnValue = codeWriter.writeTarget(RUN_RESULT_FIELD_READ_TARGET);
                            }
                        }

                        if(returnValueLocation != null) {
                            context.store("result", returnValue);
                            if(i + 1 != functions.size()) {
                                var shiftedResult = codeWriter.shiftRight(context.load("result"), new BytecodeValue.IntegerValue(32));
                                var shiftedIntResult = codeWriter.castType(shiftedResult, Type.INT_TYPE);
                                codeWriter.conditionalJump(JumpCondition.NE, shiftedIntResult, new BytecodeValue.IntegerValue(2), post);
                            }
                        }
                    }
                    codeWriter.label(post);
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes2.CHECK_ENTITY_TYPES) {
                var entity = context.load("entity");
                var entityType = callEntity_getType(codeWriter, entity);
                var success = codeWriter.createLabel();
                for(var res : context.instruction().<List<DataLocation>>get("types")) {
                    var compareType = context.fieldStorage().loadEntityType(codeWriter, res);
                    if(context.instruction().<Boolean>get("negated")) {
                        codeWriter.conditionalJump(JumpCondition.EQ, entityType, compareType, context.mapLabel("failLabel"));
                    }else {
                        codeWriter.conditionalJump(JumpCondition.EQ, entityType, compareType, success);
                    }
                }
                if(!context.instruction().<Boolean>get("negated")) {
                    codeWriter.jump(context.mapLabel("failLabel"));
                }
                codeWriter.label(success);
                return true;
            }

            return false;
        });
    }
}
