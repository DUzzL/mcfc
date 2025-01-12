package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.*;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;

public final class CodeWriter {
    private final ArrayList<BytecodeInstruction> instructions = new ArrayList<>();
    private int nextRegister = 0;
    private int nextLabel = 0;

    public BytecodeLabel createLabel() {
        return new BytecodeLabel(nextLabel++);
    }

    public BytecodeRegister createRegister() {
        return new BytecodeRegister(nextRegister++);
    }

    public BytecodeRegister writeTarget(InstructionTarget target, List<BytecodeValue> args) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.CallTarget(resultRegister, target, args));
        return resultRegister;
    }

    public BytecodeRegister newObjectCreate(InstructionTarget target, List<BytecodeValue> args) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.New(resultRegister, target, args));
        return resultRegister;
    }

    public BytecodeRegister writeTarget(InstructionTarget target, BytecodeValue... args) {
        return writeTarget(target, Arrays.asList(args));
    }

    public BytecodeRegister newObjectCreate(InstructionTarget target, BytecodeValue... args) {
        return newObjectCreate(target, Arrays.asList(args));
    }

    public BytecodeRegister writeTarget(InstructionTarget target) {
        return writeTarget(target, List.of());
    }

    public BytecodeRegister newObjectCreate(InstructionTarget target) {
        return newObjectCreate(target, List.of());
    }

    public BytecodeRegister getDataField(String name, Class<?> type) {
        return writeTarget(new InstructionTarget.FieldTarget(DATA_CLASS, name, type.descriptorString(), true, false));
    }

    public void setDataField(String name, Class<?> type, BytecodeValue value) {
        writeTarget(new InstructionTarget.FieldTarget(DATA_CLASS, name, type.descriptorString(), true, true), value);
    }

    public BytecodeRegister castType(BytecodeValue value, Class<?> type) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.Cast(resultRegister, value, type));
        return resultRegister;
    }

    public void instanceCheckJump(boolean ifIsInstance, BytecodeValue value, Class<?> clazz, BytecodeLabel label) {
        instructions.add(new BytecodeInstruction.InstanceCheckJump(ifIsInstance, value, clazz, label));
    }

    public void conditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue b, BytecodeLabel label) {
        instructions.add(new BytecodeInstruction.ConditionalJump(condition, a, b, label));
    }

    public void jump(BytecodeLabel label) {
        instructions.add(new BytecodeInstruction.Jump(label));
    }

    public void label(BytecodeLabel label) {
        instructions.add(new BytecodeInstruction.Label(label));
    }

    public BytecodeRegister stringConcat(List<BytecodeValue> args) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.StringConcat(resultRegister, args));
        return resultRegister;
    }

    public void returnVoid() {
        instructions.add(new BytecodeInstruction.ReturnVoid());
    }

    public void returnValue(BytecodeValue value) {
        instructions.add(new BytecodeInstruction.Return(value));
    }

    public BytecodeRegister cloneValue(BytecodeValue value) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.RegisterValue(InstructionRVType.SET, resultRegister, value));
        return resultRegister;
    }

    public void setValue(BytecodeRegister dest, BytecodeValue value) {
        instructions.add(new BytecodeInstruction.RegisterValue(InstructionRVType.SET, dest, value));
    }

    public BytecodeRegister add(BytecodeValue a, BytecodeValue b) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.RegisterValueValue(InstructionRVVType.ADD, resultRegister, a, b));
        return resultRegister;
    }

    public BytecodeRegister sub(BytecodeValue a, BytecodeValue b) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.RegisterValueValue(InstructionRVVType.SUB, resultRegister, a, b));
        return resultRegister;
    }

    public BytecodeRegister mul(BytecodeValue a, BytecodeValue b) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.RegisterValueValue(InstructionRVVType.MUL, resultRegister, a, b));
        return resultRegister;
    }

    public BytecodeRegister newArray(BytecodeValue length, Class<?> contentType) {
        var resultRegister = createRegister();
        instructions.add(new BytecodeInstruction.CreateArray(resultRegister, length, contentType));
        return resultRegister;
    }

    public void setArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) {
        instructions.add(new BytecodeInstruction.SetArrayValue(array, index, value));
    }

    public List<BytecodeInstruction> getInstructions() {
        return List.copyOf(instructions);
    }
}
