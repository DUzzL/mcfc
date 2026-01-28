package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface StackInstruction permits StackCompiler.InlineEvalStore, StackInstruction.ArrayStore, StackInstruction.Call, StackInstruction.Cast, StackInstruction.Evaluate, StackInstruction.EvaluateString, StackInstruction.Jump, StackInstruction.JumpTyped, StackInstruction.Label, StackInstruction.Load, StackInstruction.New, StackInstruction.NewArray, StackInstruction.Pop, StackInstruction.Return, StackInstruction.ReturnValue, StackInstruction.Store {

    record Load(BytecodeValue value, ClassDesc type) implements StackInstruction {}
    record Store(BytecodeRegister register) implements StackInstruction {}
    record Call(InstructionTarget target) implements StackInstruction {}
    record New(ClassDesc type) implements StackInstruction {}
    record NewArray(ClassDesc type) implements StackInstruction {}
    record ArrayStore() implements StackInstruction {}
    record ReturnValue() implements StackInstruction {}
    record Return() implements StackInstruction {}
    record Pop() implements StackInstruction {}
    record Evaluate(StackEvaluationType evaluationType) implements StackInstruction {}
    record EvaluateString(@Nullable String[] template) implements StackInstruction {}
    record Cast(ClassDesc type) implements StackInstruction {}
    record Jump(StackJumpType jumpType, BytecodeLabel label) implements StackInstruction {}
    record JumpTyped(StackJumpType jumpType, ClassDesc type, BytecodeLabel label) implements StackInstruction {}
    record Label(BytecodeLabel label) implements StackInstruction {}

}
