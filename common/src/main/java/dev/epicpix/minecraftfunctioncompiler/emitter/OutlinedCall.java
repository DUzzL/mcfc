package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Target of a {@code CALL_OUTLINED} instruction: a sibling method holding the tail of an execute chain
 * that was too large to stay in its caller (HotSpot does not JIT methods over 8000 bytes).
 *
 * <p>The outlined method has the caller's own descriptor: the command source stack (replaced by the
 * chain's current, modified source) followed by the caller's remaining parameters, passed through
 * unchanged: the function's argument compound, or its typed macro values in {@code macroSources} order.
 * Outlined tails never contain {@code return}, so the call's result (the no-return sentinel) is ignored.
 *
 * @param macroSources macro parameter names in descriptor order, or null when the second parameter is
 *                     the function's argument compound
 */
public record OutlinedCall(ClassDesc owner, String name, MethodTypeDesc type, @Nullable List<String> macroSources) {

    /** Emits the call for the current {@code CALL_OUTLINED} instruction of {@code context}. */
    public static void emit(EmitterContext context) {
        var call = Objects.requireNonNull(context.instruction().<OutlinedCall>get("call"));
        var arguments = new ArrayList<BytecodeValue>(call.type().parameterCount());
        arguments.add(context.load("stack"));
        if(call.macroSources() == null) {
            arguments.add(Objects.requireNonNull(context.getStatic("FunctionCompound"), "FunctionCompound"));
        }else {
            for(var source : call.macroSources()) arguments.add(Objects.requireNonNull(context.getMacro(source), source));
        }
        context.codeWriter().writeTarget(new InstructionTarget.FunctionTarget(call.owner(), call.name(), call.type(), true, false, false, true), arguments);
    }
}
