package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;

public record CodeWriter(
        MethodVisitor methodVisitor,

        HashMap<String, Local> locals,
        HashSet<Integer> availableLocals,
        ArrayList<Local> generatedLocals
) {

    public CodeWriter(MethodVisitor methodVisitor) {
        this(methodVisitor, new HashMap<>(), new HashSet<>(), new ArrayList<>());
    }

    private int allocateLocalIndex() {
        int localIndex;
        if(availableLocals.isEmpty()) {
            localIndex = locals.size();
        }else {
            localIndex = availableLocals.toArray(new Integer[0])[0];
            availableLocals.remove(localIndex);
        }
        return localIndex;
    }

    public Local createLocal(String local, String type) {
        var localStart = new Label();
        methodVisitor.visitLabel(localStart);
        var localValue = new Local(local, type, localStart, new Label(), allocateLocalIndex());
        locals.put(local, localValue);
        generatedLocals.add(localValue);
        return localValue;
    }

    public void destroyLocal(Local local) {
        methodVisitor.visitLabel(local.end());
        locals.remove(local.name());
        availableLocals.add(local.index());
    }

    public void writeLocalVariables() {
        if(DEBUG) {
            for(var it : generatedLocals) {
                methodVisitor.visitLocalVariable(it.name().replace(".", "$").replace(":", "$"), it.descriptor(), null, it.start(), it.end(), it.index());
            }
        }
    }

    public void loadLocal(String local) {
        var localVar = locals.get(local);
        switch(localVar.descriptor()) {
            case "I" -> methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index());
            case "F" -> methodVisitor.visitVarInsn(Opcodes.FLOAD, localVar.index());
            default -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index());
        }
    }

    public void storeLocal(String local) {
        var localVar = locals.get(local);
        switch(localVar.descriptor()) {
            case "I" -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVar.index());
            case "F" -> methodVisitor.visitVarInsn(Opcodes.FSTORE, localVar.index());
            default -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar.index());
        }
    }

    public void writeInt(int value) {
        if (value >= -1 && value <= 5) {
            methodVisitor.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            methodVisitor.visitLdcInsn(value);
        }
    }

    public void writeLong(long value) { methodVisitor.visitLdcInsn(value); }
    public void writeFloat(float value) { methodVisitor.visitLdcInsn(value); }
    public void writeDouble(double value) { methodVisitor.visitLdcInsn(value); }
    public void writeString(String value) { methodVisitor.visitLdcInsn(value); }
    public void writeNull() { methodVisitor.visitInsn(Opcodes.ACONST_NULL); }

    public void newObjectCreate(Class<?> owner) {
        var ownerDescriptor = owner.descriptorString();
        methodVisitor.visitTypeInsn(Opcodes.NEW, ownerDescriptor.substring(1, ownerDescriptor.length() - 1));
        methodVisitor.visitInsn(Opcodes.DUP);
    }

    public void newObjectInit(Class<?> owner, Class<?>... types) {
        var ownerName = owner.descriptorString();
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, ownerName.substring(1, ownerName.length() - 1), "<init>", "(" + Arrays.stream(types).map(Class::descriptorString).collect(Collectors.joining("")) + ")V", false);
    }

    public void getDataField(String name, Class<?> type) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, name, type.descriptorString());
    }

    public void setDataField(String name, Class<?> type) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, name, type.descriptorString());
    }

    public void castType(Class<?> type) {
        var desc = type.descriptorString();
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, desc.substring(1, desc.length() - 1));
    }

    private int getIfJumpOpcode(int comparison) {
        return switch (comparison) {
            case Opcodes.IFNONNULL -> Opcodes.IFNULL;
            case Opcodes.IFNULL -> Opcodes.IFNONNULL;
            case Opcodes.IFNE -> Opcodes.IFNE;
            case Opcodes.IFEQ -> Opcodes.IFEQ;
            case Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT;
            case Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE;
            default -> throw new UnsupportedOperationException("Unknown comparison opcode: " + comparison);
        };
    }

    public Label executeIfLabeled(int comparison) {
        var endLabel = new Label();
        methodVisitor.visitJumpInsn(getIfJumpOpcode(comparison), endLabel);
        return endLabel;
    }

    public String generateUniqueLocal() {
        return "L:" + generatedLocals.size();
    }

}
