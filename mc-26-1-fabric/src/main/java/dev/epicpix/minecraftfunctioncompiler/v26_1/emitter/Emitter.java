package dev.epicpix.minecraftfunctioncompiler.v26_1.emitter;

import dev.epicpix.minecraftfunctioncompiler.CommonConstants;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.profiler.Profiler;
import dev.epicpix.minecraftfunctioncompiler.v26_1.CompiledCommandResultStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Type;

import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callThreadLocal_get;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callThreadLocal_set;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.*;

public final class Emitter {
    private Emitter() {}

    private static final InstructionTarget COMMAND_RESULT_STORE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompiledCommandResultStore.class);
    private static final InstructionTarget RUN_RESULT_FIELD_READ_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, "J", true, false);
    private static final InstructionTarget RUN_RESULT_FIELD_WRITE_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, "J", true, true);

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS) {
                var functions = context.instruction().<List<DataLocation>>get("functions");
                if(!functions.isEmpty()) {
                    var post = codeWriter.createLabel();
                    for(var func : functions) {
                        var commandSourceStack = context.load("stack");
                        if(context.compiledFunctions().contains(func)) {
                            var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)) : new BytecodeValue.NullValue();
                            var target = new InstructionTarget.FunctionTarget(("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$'), "run", "(" + CommandSourceStack.class.descriptorString() + context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).getDescriptor() + ")J", true, false, false, true);
                            codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
                        }else {
                            var mctxtl = loadCommands_CURRENT_EXECUTION_CONTEXT(codeWriter);
                            var mctx = callThreadLocal_get(codeWriter, mctxtl);
                            callThreadLocal_set(codeWriter, mctxtl, new BytecodeValue.NullValue());
                            var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
                            var function = context.fieldStorage().loadFunction(codeWriter, func);
                            codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "enterOverhead", void.class, String.class), new BytecodeValue.StringValue(func.toString()));
                            if(context.instruction().get("arguments") != null) {
                                callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)));
                            }else {
                                callServerFunctionManager_execute(codeWriter, functionManager, function, commandSourceStack);
                            }
                            codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "exit", void.class));
                            callThreadLocal_set(codeWriter, mctxtl, mctx);
                        }
                    }
                    codeWriter.label(post);
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS_WITH_RESULT) {
                var functions = context.instruction().<List<DataLocation>>get("functions");
                if(functions.isEmpty()) {
                    context.store("result", new BytecodeValue.LongValue(1L << 32));
                }else {
                    var post = codeWriter.createLabel();
                    codeWriter.writeTarget(RUN_RESULT_FIELD_WRITE_TARGET, new BytecodeValue.LongValue(2L << 32));
                    for(int i = 0; i < functions.size(); i++) {
                        var func = functions.get(i);
                        var commandSourceStack = context.load("stack");
                        BytecodeRegister returnValue;
                        if(context.compiledFunctions().contains(func)) {
                            var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)) : new BytecodeValue.NullValue();
                            var target = new InstructionTarget.FunctionTarget(("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$'), "run", "(" + CommandSourceStack.class.descriptorString() + context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND).getDescriptor() + ")J", true, false, false, true);
                            returnValue = codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
                        }else {
                            var mctxtl = loadCommands_CURRENT_EXECUTION_CONTEXT(codeWriter);
                            var mctx = callThreadLocal_get(codeWriter, mctxtl);
                            callThreadLocal_set(codeWriter, mctxtl, new BytecodeValue.NullValue());
                            var store = codeWriter.newObjectCreate(COMMAND_RESULT_STORE_INIT_TARGET);
                            commandSourceStack = callCommandSourceStack_withCallback(codeWriter, commandSourceStack, store);
                            var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
                            var function = context.fieldStorage().loadFunction(codeWriter, func);
                            codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "enterOverhead", void.class, String.class), new BytecodeValue.StringValue(func.toString()));
                            if(context.instruction().get("arguments") != null) {
                                callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, codeWriter.castType(context.load("arguments"), Type.getType(CompoundTag.class)));
                            }else {
                                callServerFunctionManager_execute(codeWriter, functionManager, function, commandSourceStack);
                            }
                            codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "exit", void.class));
                            callThreadLocal_set(codeWriter, mctxtl, mctx);
                            returnValue = codeWriter.writeTarget(RUN_RESULT_FIELD_READ_TARGET);
                        }

                        context.store("result", returnValue);
                        if(i + 1 != functions.size()) {
                            var shiftedResult = codeWriter.shiftRight(context.load("result"), new BytecodeValue.IntegerValue(32));
                            var shiftedIntResult = codeWriter.castType(shiftedResult, Type.INT_TYPE);
                            codeWriter.conditionalJump(JumpCondition.NE, shiftedIntResult, new BytecodeValue.IntegerValue(2), post);
                        }
                    }
                    codeWriter.label(post);
                }
                return true;
            }

            return false;
        });
    }
}
