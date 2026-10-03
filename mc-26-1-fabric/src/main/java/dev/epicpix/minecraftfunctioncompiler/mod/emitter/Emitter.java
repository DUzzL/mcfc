package dev.epicpix.minecraftfunctioncompiler.mod.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterInstructions;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorageMapKind;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.loader.FrameCost;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerRecursionGuard;
import dev.epicpix.minecraftfunctioncompiler.mod.VanillaFunctionCall;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.ServerFunctionManager;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Objects;

public final class Emitter {
    private Emitter() {}


    private static final ClassDesc GUARD = FunctionCompilerRecursionGuard.class.describeConstable().orElseThrow();
    private static final InstructionTarget DEPTH_READ_TARGET = new InstructionTarget.FieldTarget(GUARD, "depth", ConstantDescs.CD_int, true, false);
    private static final InstructionTarget DEPTH_WRITE_TARGET = new InstructionTarget.FieldTarget(GUARD, "depth", ConstantDescs.CD_int, true, true);
    private static final ClassDesc CD_COMMAND_SOURCE_STACK = CommandSourceStack.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_COMPOUND_TAG = CompoundTag.class.describeConstable().orElseThrow();
    private static final InstructionTarget VANILLA_CALL_TARGET = new InstructionTarget.FunctionTarget(
        VanillaFunctionCall.class.describeConstable().orElseThrow(), "call",
        MethodTypeDesc.of(ConstantDescs.CD_long, ServerFunctionManager.class.describeConstable().orElseThrow(),
            CommandFunction.class.describeConstable().orElseThrow(), CD_COMMAND_SOURCE_STACK, CD_COMPOUND_TAG),
        true, false, false, true);

    public static void registerInstructionEmitters() {
        EmitterInstructions.registerEmitterInstruction(context -> {
            var codeWriter = context.codeWriter();
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS) {
                var functions = Objects.requireNonNull(context.instruction().<List<DataLocation>>get("functions"));
                if(!functions.isEmpty()) {
                    for(var func : functions) {
                        var commandSourceStack = context.load("stack");
                        if(context.compiledFunctions().contains(func)) {
                            var slow = codeWriter.createLabel();
                            var done = codeWriter.createLabel();
                            emitGuardedCompiledCall(context, func, commandSourceStack, slow);
                            codeWriter.jump(done);
                            codeWriter.label(slow);
                            emitVanillaCall(context, func, commandSourceStack);
                            codeWriter.label(done);
                        }else {
                            emitVanillaCall(context, func, commandSourceStack);
                        }
                    }
                }
                return true;
            }
            if(context.instruction().type() == InstructionTypes2.CALL_FUNCTIONS_WITH_RESULT) {
                var functions = Objects.requireNonNull(context.instruction().<List<DataLocation>>get("functions"));
                if(functions.isEmpty()) {
                    context.store("result", new BytecodeValue.LongValue(1L << 32));
                }else {
                    var post = codeWriter.createLabel();
                    for(int i = 0; i < functions.size(); i++) {
                        var func = functions.get(i);
                        var commandSourceStack = context.load("stack");
                        if(context.compiledFunctions().contains(func)) {
                            var slow = codeWriter.createLabel();
                            var done = codeWriter.createLabel();
                            context.store("result", emitGuardedCompiledCall(context, func, commandSourceStack, slow));
                            codeWriter.jump(done);
                            codeWriter.label(slow);
                            context.store("result", emitVanillaCall(context, func, commandSourceStack));
                            codeWriter.label(done);
                        }else {
                            context.store("result", emitVanillaCall(context, func, commandSourceStack));
                        }

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

    /**
     * Direct invokestatic of a compiled callee, charged against {@link FunctionCompilerRecursionGuard} with the
     * calling function's frame chain ({@link FrameCost#PLACEHOLDER}, patched after stack compilation).
     * Jumps to {@code atLimit} (the caller emits the vanilla path there) when the depth budget is used up,
     * so deep datapack recursion continues through vanilla's iterative executor instead of overflowing the
     * Java stack. The depth is restored from a local on normal return; exceptional exits are restored by the
     * vanilla->compiled entry point.
     */
    private static BytecodeRegister emitGuardedCompiledCall(EmitterContext context, DataLocation func, BytecodeValue commandSourceStack, BytecodeLabel atLimit) {
        var codeWriter = context.codeWriter();
        var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), CD_COMPOUND_TAG) : new BytecodeValue.NullValue();
        var depth = codeWriter.writeTarget(DEPTH_READ_TARGET);
        codeWriter.conditionalJump(JumpCondition.GE, depth, new BytecodeValue.IntegerValue(FunctionCompilerRecursionGuard.MAX_DEPTH), atLimit);
        codeWriter.writeTarget(DEPTH_WRITE_TARGET, codeWriter.add(depth, codeWriter.writeTarget(FrameCost.PLACEHOLDER)));
        var methodType = MethodTypeDesc.of(ConstantDescs.CD_long, CD_COMMAND_SOURCE_STACK, context.fieldStorage().getMap().getValueType(CodeFieldStorageMapKind.NBT_COMPOUND));
        ClassDesc targetClass;
        String targetMethod;
        var resolver = context.resolver();
        if(resolver != null) {
            var resolved = Objects.requireNonNull(resolver.resolveRun(func));
            targetClass = resolved.batchClass();
            targetMethod = resolved.methodName();
        } else {
            targetClass = ClassDesc.ofInternalName(("mcfc/function/" + func.namespace() + "/" + func.path()).replace('.', '$'));
            targetMethod = "run";
        }
        var target = new InstructionTarget.FunctionTarget(targetClass, targetMethod, methodType, true, false, false, true);
        var result = codeWriter.writeTarget(target, commandSourceStack, nbtArgs);
        codeWriter.writeTarget(DEPTH_WRITE_TARGET, depth);
        return result;
    }

    /**
     * Runs {@code func} through vanilla ({@link VanillaFunctionCall#call}) and returns its result register. The
     * calling function's frames are charged to the guard for the duration of the call, since vanilla may re-enter
     * compiled code on top of them.
     */
    private static BytecodeRegister emitVanillaCall(EmitterContext context, DataLocation func, BytecodeValue commandSourceStack) {
        var codeWriter = context.codeWriter();
        var nbtArgs = context.instruction().get("arguments") != null ? codeWriter.castType(context.load("arguments"), CD_COMPOUND_TAG) : new BytecodeValue.NullValue();
        var functionManager = context.fieldStorage().loadServerFunctionManager(codeWriter);
        var function = context.fieldStorage().loadFunction(codeWriter, func);
        var depth = codeWriter.writeTarget(DEPTH_READ_TARGET);
        codeWriter.writeTarget(DEPTH_WRITE_TARGET, codeWriter.add(depth, codeWriter.writeTarget(FrameCost.PLACEHOLDER)));
        var result = codeWriter.writeTarget(VANILLA_CALL_TARGET, functionManager, function, commandSourceStack, nbtArgs);
        codeWriter.writeTarget(DEPTH_WRITE_TARGET, depth);
        return result;
    }
}
