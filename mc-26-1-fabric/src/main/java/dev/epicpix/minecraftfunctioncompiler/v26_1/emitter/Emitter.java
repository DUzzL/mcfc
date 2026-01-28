package dev.epicpix.minecraftfunctioncompiler.v26_1.emitter;

import dev.epicpix.minecraftfunctioncompiler.CommonConstants;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Objects;

import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callThreadLocal_get;
import static dev.epicpix.minecraftfunctioncompiler.emitter.JavaMappedUsageDefinitionsExtensions.callThreadLocal_set;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.callCommandSourceStack_withCallback;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.callExtensionFunctions_executeFunction;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.callServerFunctionManager_execute;
import static dev.epicpix.minecraftfunctioncompiler.v26_1.emitter.MappedUsageDefinitionsExtensions.loadCommands_CURRENT_EXECUTION_CONTEXT;

public final class Emitter {
    private Emitter() {}

    private static final InstructionTarget COMMAND_RESULT_STORE_INIT_TARGET = InstructionTarget.FunctionTarget.initFunction(CompiledCommandResultStore.class);
    private static final InstructionTarget RUN_RESULT_FIELD_READ_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, ConstantDescs.CD_long, true, false);
    private static final InstructionTarget RUN_RESULT_FIELD_WRITE_TARGET = new InstructionTarget.FieldTarget(CommonConstants.DATA_CLASS, CommonConstants.RUN_RESULT_FIELD, ConstantDescs.CD_long, true, true);

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS) {
                var functions = Objects.requireNonNull(context.instruction().<List<DataLocation>>get("functions"));
                if(!functions.isEmpty()) {
                    var post = codeWriter.createLabel();
                    for(var func : functions) {
                        var commandSourceStack = context.load("stack");
                        if(context.compiledFunctions().contains(func)) {
                            var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), CompoundTag.class.describeConstable().orElseThrow()) : new BytecodeValue.NullValue();
                            var className = ("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$');
                            var target = new InstructionTarget.FunctionTarget(ClassDesc.ofInternalName(className), "run", MethodTypeDesc.of(ConstantDescs.CD_long, CommandSourceStack.class.describeConstable().orElseThrow(), context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND)), true, false, false, true);
                            codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
                        }else {
                            var mctxtl = loadCommands_CURRENT_EXECUTION_CONTEXT(codeWriter);
                            var mctx = callThreadLocal_get(codeWriter, mctxtl);
                            callThreadLocal_set(codeWriter, mctxtl, new BytecodeValue.NullValue());
                            var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
                            var function = context.fieldStorage().loadFunction(codeWriter, func);
                            codeWriter.writeTarget(InstructionTarget.FunctionTarget.staticFunction(Profiler.class, "enterOverhead", void.class, String.class), new BytecodeValue.StringValue(func.toString()));
                            if(context.instruction().get("arguments") != null) {
                                callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, codeWriter.castType(context.load("arguments"), CompoundTag.class.describeConstable().orElseThrow()));
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
                var functions = Objects.requireNonNull(context.instruction().<List<DataLocation>>get("functions"));
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
                            var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), CompoundTag.class.describeConstable().orElseThrow()) : new BytecodeValue.NullValue();
                            var className = ("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$');
                            var target = new InstructionTarget.FunctionTarget(ClassDesc.ofInternalName(className), "run", MethodTypeDesc.of(ConstantDescs.CD_long, CommandSourceStack.class.describeConstable().orElseThrow(), context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND)), true, false, false, true);
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
                                callExtensionFunctions_executeFunction(codeWriter, functionManager, function, commandSourceStack, codeWriter.castType(context.load("arguments"), CompoundTag.class.describeConstable().orElseThrow()));
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
                            var shiftedIntResult = codeWriter.castType(shiftedResult, ConstantDescs.CD_int);
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
